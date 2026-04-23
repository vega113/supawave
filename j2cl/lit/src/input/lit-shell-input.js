/**
 * @typedef {object} LitShellSnapshot
 * @property {boolean} signedIn
 * @property {string} address
 * @property {"admin"|"owner"|"user"} role
 * @property {string} domain
 * @property {string} idSeed
 * @property {string[]} features
 * @property {string} websocketAddress
 *
 * @typedef {object} LitShellInput
 * @property {() => LitShellSnapshot} read
 */

export const EMPTY_SNAPSHOT = Object.freeze({
  signedIn: false,
  address: "",
  role: "user",
  domain: "",
  idSeed: "",
  features: Object.freeze([]),
  websocketAddress: ""
});
