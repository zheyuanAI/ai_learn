import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import { compileScript, parse } from "@vue/compiler-sfc";
import ts from "typescript";
import * as vue from "vue";
import { describe, expect, it, vi } from "vitest";

/** 用途：构造可手动完成的 API 响应；无入参，返回 promise 与完成回调，用于复现分页乱序。 */
function deferred() {
  let resolve!: (value: any) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<any>((success, failure) => { resolve = success; reject = failure; });
  return { promise, resolve, reject };
}

/** 用途：运行用户管理页面真实 script setup；入参为 getUsers 替身，返回页面状态和调用记录，不访问服务或 DOM。 */
function createUserList(getUsers: ReturnType<typeof vi.fn>) {
  const filename = "UserList.vue";
  const { descriptor } = parse(readFileSync(new URL(`../${filename}`, import.meta.url), "utf8"));
  const script = compileScript(descriptor, { id: filename });
  const source = ts.transpileModule(script.content, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText;
  const exports: any = {};
  const error = vi.fn();
  runInNewContext(source, {
    exports,
    require: (name: string) => {
      if (name === "vue") return { ...vue, onMounted: vi.fn() };
      if (name === "element-plus") return { ElMessage: { error, success: vi.fn() }, ElMessageBox: { confirm: vi.fn() } };
      if (name.includes("api/admin")) return { getUsers };
      if (name.includes("stores/auth")) return { useAuthStore: () => ({ user: null }) };
      return {};
    },
  });
  return { state: exports.default.setup({}, { expose: vi.fn() }), error };
}

describe("用户管理分页请求顺序", () => {
  it("新页先完成后，旧页慢响应不得覆盖用户与总数", async () => {
    const first = deferred();
    const second = deferred();
    const getUsers = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { state } = createUserList(getUsers);
    const oldLoad = state.fetchUserList();
    state.queryParams.page = 2;
    const newLoad = state.fetchUserList();
    expect(getUsers.mock.calls.map(([params]) => params.page)).toEqual([1, 2]);
    second.resolve({ data: { records: [{ id: "new" }], total: 21 } });
    await newLoad;
    first.resolve({ data: { records: [{ id: "old" }], total: 11 } });
    await oldLoad;
    expect(state.userList.value.map((user: { id: string }) => user.id)).toEqual(["new"]);
    expect(state.totalCount.value).toBe(21);
  });

  it("旧请求失败时不得提前终止新页加载或显示旧错误", async () => {
    const first = deferred();
    const second = deferred();
    const getUsers = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { state, error } = createUserList(getUsers);
    const oldLoad = state.fetchUserList();
    state.queryParams.page = 2;
    const newLoad = state.fetchUserList();
    first.reject(new Error("旧页失败"));
    await oldLoad;
    expect(state.isLoading.value).toBe(true);
    expect(state.errorMessage.value).toBe("");
    expect(error).not.toHaveBeenCalled();
    second.resolve({ data: { records: [{ id: "new" }], total: 21 } });
    await newLoad;
    expect(state.isLoading.value).toBe(false);
    expect(state.userList.value[0].id).toBe("new");
  });
});
