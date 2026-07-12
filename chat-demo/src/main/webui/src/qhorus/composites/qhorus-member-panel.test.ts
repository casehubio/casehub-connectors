import { describe, it, expect, afterEach } from 'vitest';
import './qhorus-member-panel.js';
import type { QhorusMemberPanel } from './qhorus-member-panel.js';
import type { ChannelMember, PresenceState } from '../types.js';

describe('qhorus-member-panel', () => {
  let element: QhorusMemberPanel;

  afterEach(() => {
    element?.remove();
  });

  it('renders member list sorted by presence', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Alice', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Bob', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm3', displayName: 'Charlie', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'OFFLINE' },
      { memberId: 'm2', status: 'ONLINE' },
      { memberId: 'm3', status: 'AWAY' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const memberItems = element.shadowRoot!.querySelectorAll('.member-item');
    expect(memberItems.length).toBe(3);

    // Order should be: Bob (ONLINE), Charlie (AWAY), Alice (OFFLINE)
    const names = Array.from(memberItems).map((item) =>
      item.querySelector('.member-name')?.textContent?.trim()
    );
    expect(names).toEqual(['Bob', 'Charlie', 'Alice']);
  });

  it('shows presence dots with correct indicators', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Online User', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Away User', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm3', displayName: 'Offline User', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'ONLINE' },
      { memberId: 'm2', status: 'AWAY' },
      { memberId: 'm3', status: 'OFFLINE' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const dots = element.shadowRoot!.querySelectorAll('.presence-dot');
    expect(dots.length).toBe(3);
    expect(dots[0].classList.contains('dot-online')).toBe(true);
    expect(dots[1].classList.contains('dot-away')).toBe(true);
    expect(dots[2].classList.contains('dot-offline')).toBe(true);
  });

  it('shows role badges for MODERATOR and OBSERVER', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Moderator', role: 'MODERATOR' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Observer', role: 'OBSERVER' },
      { channelId: 'c1', memberId: 'm3', displayName: 'Participant', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'ONLINE' },
      { memberId: 'm2', status: 'ONLINE' },
      { memberId: 'm3', status: 'ONLINE' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const badges = element.shadowRoot!.querySelectorAll('.role-badge');
    expect(badges.length).toBe(3);
    expect(badges[0].textContent).toBe('🛡️');
    expect(badges[1].textContent).toBe('👁️');
    expect(badges[2].textContent).toBe('');
  });

  it('groups under section headers (Online, Away, Offline)', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Alice', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Bob', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm3', displayName: 'Charlie', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm4', displayName: 'Dave', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm5', displayName: 'Eve', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'ONLINE' },
      { memberId: 'm2', status: 'AVAILABLE' },
      { memberId: 'm3', status: 'BUSY' },
      { memberId: 'm4', status: 'AWAY' },
      { memberId: 'm5', status: 'OFFLINE' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const headers = element.shadowRoot!.querySelectorAll('.section-header');
    expect(headers.length).toBe(3);
    expect(headers[0].textContent?.trim()).toBe('Online');
    expect(headers[1].textContent?.trim()).toBe('Away');
    expect(headers[2].textContent?.trim()).toBe('Offline');
  });

  it('handles empty member list', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    element.members = [];
    element.presence = [];
    document.body.appendChild(element);
    await element.updateComplete;

    const emptyState = element.shadowRoot!.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent?.trim()).toBe('No members');
  });

  it('members without presence entry default to OFFLINE', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Alice', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Bob', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'ONLINE' },
      // m2 has no presence entry
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const memberItems = element.shadowRoot!.querySelectorAll('.member-item');
    expect(memberItems.length).toBe(2);

    // Alice should be first (ONLINE), Bob second (OFFLINE default)
    const names = Array.from(memberItems).map((item) =>
      item.querySelector('.member-name')?.textContent?.trim()
    );
    expect(names).toEqual(['Alice', 'Bob']);

    const dots = element.shadowRoot!.querySelectorAll('.presence-dot');
    expect(dots[0].classList.contains('dot-online')).toBe(true);
    expect(dots[1].classList.contains('dot-offline')).toBe(true);
  });

  it('shows status message when present', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Alice', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'BUSY', statusMessage: 'In a meeting' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const statusMessage = element.shadowRoot!.querySelector('.status-message');
    expect(statusMessage?.textContent?.trim()).toBe('In a meeting');
  });

  it('renders only Online header when all members online', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Alice', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Bob', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm3', displayName: 'Charlie', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'ONLINE' },
      { memberId: 'm2', status: 'ONLINE' },
      { memberId: 'm3', status: 'ONLINE' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const headers = element.shadowRoot!.querySelectorAll('.section-header');
    expect(headers.length).toBe(1);
    expect(headers[0].textContent?.trim()).toBe('Online');

    const memberItems = element.shadowRoot!.querySelectorAll('.member-item');
    expect(memberItems.length).toBe(3);
  });

  it('sorts members alphabetically within same group', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Charlie', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Alice', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm3', displayName: 'Bob', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'ONLINE' },
      { memberId: 'm2', status: 'ONLINE' },
      { memberId: 'm3', status: 'ONLINE' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const memberItems = element.shadowRoot!.querySelectorAll('.member-item');
    const names = Array.from(memberItems).map((item) =>
      item.querySelector('.member-name')?.textContent?.trim()
    );
    expect(names).toEqual(['Alice', 'Bob', 'Charlie']);
  });

  it('maps AVAILABLE and BUSY to Online group with dot-online class', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Alice', role: 'PARTICIPANT' },
      { channelId: 'c1', memberId: 'm2', displayName: 'Bob', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'AVAILABLE' },
      { memberId: 'm2', status: 'BUSY' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const headers = element.shadowRoot!.querySelectorAll('.section-header');
    expect(headers.length).toBe(1);
    expect(headers[0].textContent?.trim()).toBe('Online');

    const dots = element.shadowRoot!.querySelectorAll('.presence-dot');
    expect(dots.length).toBe(2);
    expect(dots[0].classList.contains('dot-online')).toBe(true);
    expect(dots[1].classList.contains('dot-online')).toBe(true);
  });

  it('does not render status-message element when absent', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    const members: ChannelMember[] = [
      { channelId: 'c1', memberId: 'm1', displayName: 'Alice', role: 'PARTICIPANT' },
    ];
    const presence: PresenceState[] = [
      { memberId: 'm1', status: 'ONLINE' },
    ];

    element.members = members;
    element.presence = presence;
    document.body.appendChild(element);
    await element.updateComplete;

    const memberItem = element.shadowRoot!.querySelector('.member-item');
    const statusMessage = memberItem?.querySelector('.status-message');
    expect(statusMessage).toBeNull();
  });

  it('renders empty state when members array is empty', async () => {
    element = document.createElement('qhorus-member-panel') as QhorusMemberPanel;
    element.members = [];
    element.presence = [];
    document.body.appendChild(element);
    await element.updateComplete;

    const emptyState = element.shadowRoot!.querySelector('.empty-state');
    expect(emptyState).toBeTruthy();
    expect(emptyState?.textContent?.trim()).toBe('No members');

    const memberItems = element.shadowRoot!.querySelectorAll('.member-item');
    expect(memberItems.length).toBe(0);
  });
});
