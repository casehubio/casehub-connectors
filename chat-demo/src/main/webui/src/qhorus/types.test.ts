// chat-demo/src/main/webui/src/qhorus/types.test.ts
import { describe, it, expect } from 'vitest';
import {
  isTerminalMessageType, isObligationCreating,
  messageTypeCategory, commitmentStateCategory,
  MESSAGE_TYPES, ACTOR_TYPES, COMMITMENT_STATES, CHANNEL_SEMANTICS, ARTEFACT_TYPES,
} from './types.js';

describe('MessageType helpers', () => {
  it('identifies terminal types', () => {
    expect(isTerminalMessageType('DONE')).toBe(true);
    expect(isTerminalMessageType('FAILURE')).toBe(true);
    expect(isTerminalMessageType('DECLINE')).toBe(true);
    expect(isTerminalMessageType('HANDOFF')).toBe(true);
    expect(isTerminalMessageType('QUERY')).toBe(false);
    expect(isTerminalMessageType('COMMAND')).toBe(false);
    expect(isTerminalMessageType('RESPONSE')).toBe(false);
    expect(isTerminalMessageType('STATUS')).toBe(false);
    expect(isTerminalMessageType('EVENT')).toBe(false);
  });

  it('identifies obligation-creating types', () => {
    expect(isObligationCreating('COMMAND')).toBe(true);
    expect(isObligationCreating('QUERY')).toBe(false);
    expect(isObligationCreating('RESPONSE')).toBe(false);
    expect(isObligationCreating('STATUS')).toBe(false);
    expect(isObligationCreating('DONE')).toBe(false);
    expect(isObligationCreating('FAILURE')).toBe(false);
    expect(isObligationCreating('DECLINE')).toBe(false);
    expect(isObligationCreating('HANDOFF')).toBe(false);
    expect(isObligationCreating('EVENT')).toBe(false);
  });

  it('categorises every message type', () => {
    for (const type of MESSAGE_TYPES) {
      expect(messageTypeCategory(type)).toBeTruthy();
    }
    expect(messageTypeCategory('QUERY')).toBe('info');
    expect(messageTypeCategory('RESPONSE')).toBe('info');
    expect(messageTypeCategory('STATUS')).toBe('info');
    expect(messageTypeCategory('COMMAND')).toBe('obligation');
    expect(messageTypeCategory('DONE')).toBe('success');
    expect(messageTypeCategory('FAILURE')).toBe('danger');
    expect(messageTypeCategory('DECLINE')).toBe('warning');
    expect(messageTypeCategory('HANDOFF')).toBe('transfer');
    expect(messageTypeCategory('EVENT')).toBe('telemetry');
  });

  it('categorises every commitment state', () => {
    for (const state of COMMITMENT_STATES) {
      expect(commitmentStateCategory(state)).toBeTruthy();
    }
    expect(commitmentStateCategory('OPEN')).toBe('active');
    expect(commitmentStateCategory('ACKNOWLEDGED')).toBe('info');
    expect(commitmentStateCategory('FULFILLED')).toBe('success');
    expect(commitmentStateCategory('FAILED')).toBe('danger');
    expect(commitmentStateCategory('DECLINED')).toBe('neutral');
    expect(commitmentStateCategory('DELEGATED')).toBe('transfer');
    expect(commitmentStateCategory('EXPIRED')).toBe('warning');
  });

  it('all enum arrays are non-empty', () => {
    expect(MESSAGE_TYPES.length).toBe(9);
    expect(ACTOR_TYPES.length).toBe(3);
    expect(COMMITMENT_STATES.length).toBe(7);
    expect(CHANNEL_SEMANTICS.length).toBe(5);
    expect(ARTEFACT_TYPES.length).toBe(8);
  });
});
