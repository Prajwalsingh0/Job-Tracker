import { describe, expect, it } from 'vitest';
import { KANBAN_COLUMNS, STATUS_COLORS, STATUS_LABELS, type JobStatus } from './index';

/**
 * These invariants are what keep the pipeline board and the status badges in sync.
 * If a new status is added to the backend enum without updating these maps, these fail.
 */
describe('job status metadata', () => {
  it('labels every status', () => {
    const statuses: JobStatus[] = [
      'wishlist',
      'applied',
      'phone_screen',
      'interview',
      'offer',
      'rejected',
      'withdrawn',
      'ghosted',
    ];

    statuses.forEach((status) => {
      expect(STATUS_LABELS[status], `missing label for ${status}`).toBeTruthy();
      expect(STATUS_COLORS[status], `missing colour for ${status}`).toBeTruthy();
    });
  });

  it('gives every kanban column a unique id that maps to a known status', () => {
    const ids = KANBAN_COLUMNS.map((column) => column.id);

    expect(new Set(ids).size).toBe(ids.length);
    ids.forEach((id) => {
      expect(STATUS_LABELS[id]).toBeTruthy();
      expect(columnHasTitleAndColour(id)).toBe(true);
    });
  });

  it('covers every status on the kanban board', () => {
    const boardIds = KANBAN_COLUMNS.map((column) => column.id).sort();
    const allStatuses = Object.keys(STATUS_LABELS).sort();

    expect(boardIds).toEqual(allStatuses);
  });
});

function columnHasTitleAndColour(id: JobStatus): boolean {
  const column = KANBAN_COLUMNS.find((candidate) => candidate.id === id);
  return Boolean(column && column.title && column.color);
}
