import { ref, readonly } from "vue";
import { generateIdempotencyKey } from "../utils/request";

/**
 * 业务命令生命周期管理组合式函数。
 *
 * 用途：为单个业务命令（如"提交订单""确认收货"）管理幂等键的完整生命周期，
 *       防止网络超时重试时自动换键导致后端重复执行命令。
 *
 * 核心规则：
 *   1. 创建时立即生成一个幂等键
 *   2. 命令成功 → 自动 reset()，生成新键准备下一个命令
 *   3. 409 冲突 → 先复读事实、保留当前键且不允许重试
 *   4. 503 / 超时 / 网络错误 → 保留当前键，可通过 retry() 复用同键重试
 *   5. 用户主动放弃 → 调用 reset() 清除当前键
 *
 * 入参：无
 * 出参：
 *   - idempotencyKey: 当前幂等键（只读响应式引用）
 *   - isExecuting: 命令是否正在执行中（防双击）
 *   - lastError: 上次执行的错误信息
 *   - canRetry: 当前是否处于可重试状态
 *   - execute(fn): 使用当前幂等键执行业务函数
 *   - reset(): 生成新键并重置状态
 */
export function useCommand() {
  const idempotencyKey = ref(generateIdempotencyKey());
  const isExecuting = ref(false);
  const lastError = ref<any>(null);
  const canRetry = ref(false);
  let retryOperation: ((key: string) => Promise<unknown>) | null = null;
  let retryOptions: ExecuteOptions | undefined;

  /**
   * 用途：描述命令冲突后的事实复读回调。
   * 入参：onConflict 在收到 409 后读取后端当前事实。
   * 出参：无；用于结束不确定命令，页面不得以旧键再次提交。
   */
  type ExecuteOptions = { onConflict?: () => Promise<void> };

  /**
   * 使用当前幂等键执行业务命令。
   *
   * 入参：fn — 接受 idempotencyKey 字符串的异步业务函数
   * 出参：业务函数的返回结果
   *
   * 流程：
   *   1. 检查是否正在执行（防双击）
   *   2. 清除上次错误，标记执行中
   *   3. 调用 fn(currentKey)，由调用方将 key 放入请求 headers
   *   4. 成功后自动 reset()，失败时根据错误类型决定是否允许重试
   */
  async function execute<T>(fn: (key: string) => Promise<T>, options?: ExecuteOptions): Promise<T> {
    if (isExecuting.value) {
      throw new Error("命令正在执行中，请勿重复提交");
    }
    if (canRetry.value && retryOperation && fn !== retryOperation) {
      throw new Error("当前命令结果不确定，请先重试原操作或显式重置");
    }

    isExecuting.value = true;
    lastError.value = null;
    canRetry.value = false;
    retryOperation = fn;
    retryOptions = options;

    try {
      const result = await fn(idempotencyKey.value);
      // 命令成功，自动准备下一个命令
      reset();
      return result;
    } catch (error: any) {
      lastError.value = error;

      // 判断错误是否可重试（保留当前幂等键）
      const isRetryable = error?.retryable === true
        || error?.httpStatus === 503
        || error?.httpStatus === 502
        || error?.code === 502
        || error?.code === 503
        || error?.message?.includes("超时")
        || error?.message?.includes("timeout")
        || error?.message?.includes("Network Error")
        || error?.message?.includes("网络");

      // 409 冲突意味着命令可能已经执行：先复读后端事实，当前键仅保留审计语义。
      const isConflict = error?.httpStatus === 409 || error?.code === 409;

      if (isConflict) {
        canRetry.value = false;
        if (options?.onConflict) await options.onConflict();
        // 409 已完成复读且当前命令结束；下一个明确操作使用新键，旧键绝不重放。
        idempotencyKey.value = generateIdempotencyKey();
      } else if (isRetryable) {
        // 结果不确定，保留当前键允许重试
        canRetry.value = true;
      } else if ([403, 404, 422].includes(error?.httpStatus) || [403, 404, 422].includes(error?.code)) {
        // 明确失败仅在权限、资源或参数校验错误后开始新命令。
        canRetry.value = false;
        idempotencyKey.value = generateIdempotencyKey();
      } else {
        // 其他失败不擅自换键，避免把不确定结果扩展为第二条业务命令。
        canRetry.value = false;
      }

      throw error;
    } finally {
      isExecuting.value = false;
    }
  }

  /**
   * 用途：在可重试的网络不确定性错误后，以原幂等键重放上一次命令。
   * 入参：无。
   * 出参：上一次命令的异步返回结果；非可重试状态直接拒绝。
   */
  async function retry<T>(): Promise<T> {
    if (!canRetry.value || !retryOperation) {
      throw new Error("当前命令不可重试");
    }
    return execute(retryOperation as (key: string) => Promise<T>, retryOptions);
  }

  /**
   * 重置命令状态：生成新的幂等键，清除错误和重试标记。
   * 在用户明确开始新的业务操作或放弃重试时调用。
   */
  function reset() {
    idempotencyKey.value = generateIdempotencyKey();
    lastError.value = null;
    canRetry.value = false;
    isExecuting.value = false;
  }

  return {
    idempotencyKey: readonly(idempotencyKey),
    isExecuting: readonly(isExecuting),
    lastError: readonly(lastError),
    canRetry: readonly(canRetry),
    execute,
    retry,
    reset,
  };
}
