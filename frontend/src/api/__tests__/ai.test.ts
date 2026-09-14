import { describe, expect, it } from "vitest";
import { parseAiSseFrames } from "../ai";

describe("parseAiSseFrames", () => {
  it("解析跨事件的命名 SSE 与多行 data", () => {
    const events = parseAiSseFrames([
      "event: meta\ndata: {\"request_id\":\"req-1\"}",
      "event: delta\ndata: {\"text\":\"库存正常\"}",
    ].join("\n\n"));

    expect(events).toEqual([
      { name: "meta", data: { request_id: "req-1" } },
      { name: "delta", data: { text: "库存正常" } },
    ]);
  });

  it("忽略未知事件和非法 JSON", () => {
    expect(parseAiSseFrames("event: redirect\ndata: {\"url\":\"https://unsafe.example\"}")).toEqual([]);
    expect(parseAiSseFrames("event: delta\ndata: not-json")).toEqual([]);
  });
});
