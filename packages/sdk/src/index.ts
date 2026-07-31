export type {
  DeviceFrame,
  DeviceInfo,
  MessageStatus,
  SimSlot,
} from "@simwire/protocol";
export type { StoredConfig } from "./config.js";
export { clearConfig, configPath, loadConfig, saveConfig } from "./config.js";
export type { ConnectOptions } from "./connect.js";
export { connect } from "./connect.js";
export type { DiscoveredDevice } from "./discovery.js";
export { discoverDevice } from "./discovery.js";
export type { SentMessageStatus } from "./message.js";
export { MessageFailedError, SentMessage, TimeoutError } from "./message.js";
export type { MockOptions, SimulateIncomingOptions } from "./mock.js";
export { mock, MockSimwire } from "./mock.js";
export type {
  DeviceState,
  IncomingMessage,
  ReconnectOptions,
  Reopen,
  SendOptions,
} from "./simwire.js";
export { Simwire } from "./simwire.js";
export type { Transport } from "./transport.js";

import { connect } from "./connect.js";
import { mock } from "./mock.js";
import { Simwire } from "./simwire.js";

// Ergonomic aliases so both styles work:
//   import { connect } from "simwire"          -> await connect()
//   import { Simwire } from "simwire"          -> await Simwire.connect()
Simwire.connect = connect;
Simwire.mock = mock;
