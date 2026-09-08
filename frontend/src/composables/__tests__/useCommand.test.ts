import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../../utils/request";
import { useCommand } from "../useCommand";

describe("useCommand", () => {
  it("执行中拒绝第二次调用", async () => {
    const command = useCommand();
    let resolveFirst!: (value: string) => void;
    const first = command.execute(() => new Promise<string>((resolve) => { resolveFirst = resolve; }));

    await expect(command.execute(async () => "duplicate")).rejects.toThrow("命令正在执行中");
    resolveFirst("ok");
    await expect(first).resolves.toBe("ok");
  });

  it("503 后复用同一幂等键重试，成功后换新键", async () => {
    const command = useCommand();
    const keys: string[] = [];
    const operation = vi.fn(async (key: string) => {
      keys.push(key);
      if (keys.length === 1) throw new ApiError({ message: "暂不可用", httpStatus: 503, retryable: true });
      return "ok";
    });

    await expect(command.execute(operation)).rejects.toBeInstanceOf(ApiError);
    expect(command.canRetry.value).toBe(true);
    await expect(command.retry()).resolves.toBe("ok");
    expect(keys[0]).toBe(keys[1]);
    expect(command.idempotencyKey.value).not.toBe(keys[0]);
  });

  it("409 先复读事实并结束当前命令", async () => {
    const command = useCommand();
    const originalKey = command.idempotencyKey.value;
    const reconcile = vi.fn(async () => undefined);

    await expect(command.execute(
      async () => { throw new ApiError({ message: "冲突", httpStatus: 409 }); },
      { onConflict: reconcile },
    )).rejects.toBeInstanceOf(ApiError);

    expect(reconcile).toHaveBeenCalledOnce();
    expect(command.canRetry.value).toBe(false);
    expect(command.idempotencyKey.value).not.toBe(originalKey);
  });

  it("明确业务失败为下一次业务操作生成新键", async () => {
    const command = useCommand();
    const originalKey = command.idempotencyKey.value;
    await expect(command.execute(async () => {
      throw new ApiError({ message: "参数错误", httpStatus: 422 });
    })).rejects.toBeInstanceOf(ApiError);
    expect(command.idempotencyKey.value).not.toBe(originalKey);
    expect(command.canRetry.value).toBe(false);
  });

  it("重试返回 409 时仍复用首次冲突复读回调", async () => {
    const command = useCommand();
    let calls = 0;
    const reconcile = vi.fn(async () => undefined);
    const operation = async () => {
      calls += 1;
      throw new ApiError({ message: "失败", httpStatus: calls === 1 ? 503 : 409, retryable: calls === 1 });
    };
    await expect(command.execute(operation, { onConflict: reconcile })).rejects.toBeInstanceOf(ApiError);
    await expect(command.retry()).rejects.toBeInstanceOf(ApiError);
    expect(reconcile).toHaveBeenCalledOnce();
  });

  it("可重试失败时拒绝不同命令，reset 后允许新命令", async () => {
    const command = useCommand();
    await expect(command.execute(async () => { throw new ApiError({ message: "网络", httpStatus: 503, retryable: true }); })).rejects.toBeInstanceOf(ApiError);
    await expect(command.execute(async () => "other")).rejects.toThrow("请先重试原操作或显式重置");
    command.reset();
    await expect(command.execute(async () => "other")).resolves.toBe("other");
  });
});
