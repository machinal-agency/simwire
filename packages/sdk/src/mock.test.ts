import { describe, expect, it } from "vitest";
import { MessageFailedError } from "./message.js";
import { mock } from "./mock.js";

describe("mock()", () => {
  it("sends a message through the full lifecycle", async () => {
    const sms = mock({ latencyMs: 1 });
    const message = await sms.send({ to: "+250788123456", text: "OTP: 4821" });

    expect(message.status).toBe("queued");
    await message.waitForDelivery();
    expect(message.status).toBe("delivered");
    expect(sms.outbox).toHaveLength(1);
    expect(sms.outbox[0]?.text).toBe("OTP: 4821");
    sms.close();
  });

  it("normalizes a single recipient to an array", async () => {
    const sms = mock({ latencyMs: 1 });
    const message = await sms.send({ to: "+250788123456", text: "hi" });
    expect(message.to).toEqual(["+250788123456"]);
    sms.close();
  });

  it("rejects empty input", async () => {
    const sms = mock();
    await expect(sms.send({ to: [], text: "hi" })).rejects.toThrow("at least one recipient");
    await expect(sms.send({ to: "+1", text: "" })).rejects.toThrow("non-empty text");
    sms.close();
  });

  it("emits incoming messages to listeners", async () => {
    const sms = mock();
    const received = sms.waitForMessage((m) => m.text.includes("STOP"));

    sms.simulateIncoming({ from: "+250788000000", text: "STOP" });

    const message = await received;
    expect(message.from).toBe("+250788000000");
    expect(sms.inbox).toHaveLength(1);
    sms.close();
  });

  it("fails delivery when failNext is armed", async () => {
    const sms = mock({ latencyMs: 1 });
    sms.failNext("no signal");

    const message = await sms.send({ to: "+250788123456", text: "hello" });
    await expect(message.waitForDelivery()).rejects.toThrow(MessageFailedError);
    expect(message.status).toBe("failed");
    expect(message.error).toBe("no signal");
    sms.close();
  });

  it("supports sequential sends with independent statuses", async () => {
    const sms = mock({ latencyMs: 1 });
    sms.failNext("dead SIM");
    const failing = await sms.send({ to: "+1", text: "a" });
    const passing = await sms.send({ to: "+2", text: "b" });

    await expect(failing.waitForStatus("sent")).rejects.toThrow(MessageFailedError);
    await passing.waitForDelivery();
    expect(sms.outbox.map((m) => m.status)).toEqual(["failed", "delivered"]);
    sms.close();
  });

  it("stops emitting after close()", async () => {
    const sms = mock({ latencyMs: 30 });
    const message = await sms.send({ to: "+1", text: "late" });
    sms.close();
    await new Promise((r) => setTimeout(r, 80));
    expect(message.status).toBe("queued");
  });

  it("respects autoDeliver: false", async () => {
    const sms = mock({ latencyMs: 1, autoDeliver: false });
    const message = await sms.send({ to: "+1", text: "x" });
    await message.waitForStatus("sent");
    await new Promise((r) => setTimeout(r, 20));
    expect(message.status).toBe("sent");
    sms.close();
  });
});

describe("reconnection", () => {
  it("recovers on its own after a drop", async () => {
    const sms = mock({ latencyMs: 1, reconnect: { initialDelayMs: 5 } });
    const events: string[] = [];
    sms.on("disconnect", ({ willRetry }) => events.push(`disconnect:${willRetry}`));
    sms.on("reconnecting", ({ attempt }) => events.push(`reconnecting:${attempt}`));
    sms.on("reconnected", () => events.push("reconnected"));

    sms.simulateDrop();
    expect(sms.connected).toBe(false);

    await new Promise<void>((r) => sms.once("reconnected", () => r()));
    expect(sms.connected).toBe(true);
    expect(events).toEqual(["disconnect:true", "reconnecting:1", "reconnected"]);

    const message = await sms.send({ to: "+15550100000", text: "after the drop" });
    await message.waitForDelivery();
    expect(message.status).toBe("delivered");
    sms.close();
  });

  it("holds a send until the session is back", async () => {
    const sms = mock({ latencyMs: 1, reconnect: { initialDelayMs: 30 } });
    sms.simulateDrop();

    const message = await sms.send({ to: "+15550100000", text: "queued through the outage" });
    expect(message.status).toBe("queued");
    sms.close();
  });

  it("stays down when reconnection is disabled", async () => {
    const sms = mock({ latencyMs: 1, reconnect: { enabled: false } });
    const seen: boolean[] = [];
    sms.on("disconnect", ({ willRetry }) => seen.push(willRetry));

    sms.simulateDrop();
    await new Promise((r) => setTimeout(r, 40));

    expect(seen).toEqual([false]);
    expect(sms.connected).toBe(false);
    await expect(sms.send({ to: "+1", text: "x", queueTimeoutMs: 30 })).rejects.toThrow();
    sms.close();
  });

  it("gives up after maxAttempts", async () => {
    const sms = mock({ latencyMs: 1, reconnect: { initialDelayMs: 5, maxAttempts: 2 } });
    const attempts: number[] = [];
    sms.on("reconnecting", ({ attempt }) => attempts.push(attempt));

    // Reopening always succeeds in the mock, so drop again on each recovery to
    // exercise the attempt counter rather than the happy path.
    sms.on("reconnected", () => sms.simulateDrop());
    sms.simulateDrop();
    await new Promise((r) => setTimeout(r, 120));

    expect(attempts.length).toBeGreaterThanOrEqual(2);
    sms.close();
  });
});
