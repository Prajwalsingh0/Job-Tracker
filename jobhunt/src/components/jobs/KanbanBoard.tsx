import { useState } from 'react';
import {
  DndContext,
  DragOverlay,
  closestCorners,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragStartEvent,
  DragEndEvent,
} from '@dnd-kit/core';
import { useJobs } from '@/context/JobContext';
import { Job, JobStatus, KANBAN_COLUMNS } from '@/types';
import { KanbanColumn } from './KanbanColumn';
import { JobCard } from './JobCard';
import { Modal } from '@/components/ui/Modal';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { useToast } from '@/components/ui/Toast';
import { JobForm } from './JobForm';

export function KanbanBoard() {
  const { state, moveJob, deleteJob, getJobsByStatus } = useJobs();
  const { showToast } = useToast();
  const [activeJob, setActiveJob] = useState<Job | null>(null);
  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<Job | null>(null);
  // On phones eight columns side by side are unusable, so one stage is shown at a time.
  const [mobileColumn, setMobileColumn] = useState<JobStatus>('wishlist');

  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: {
        distance: 8,
      },
    }),
    useSensor(KeyboardSensor)
  );

  const handleDragStart = (event: DragStartEvent) => {
    const job = state.jobs.find((candidate) => String(candidate.id) === String(event.active.id));
    if (job) {
      setActiveJob(job);
    }
  };

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    setActiveJob(null);

    if (!over) return;

    const jobId = Number(active.id);
    const overId = over.id;

    // Dropping on a column header and dropping on a card inside that column should both work.
    const targetColumn =
      KANBAN_COLUMNS.find((column) => String(column.id) === String(overId)) ??
      KANBAN_COLUMNS.find(
        (column) =>
          column.id ===
          state.jobs.find((job) => String(job.id) === String(overId))?.status
      );

    if (!targetColumn) return;

    const draggedJob = state.jobs.find((job) => job.id === jobId);
    if (draggedJob && draggedJob.status === targetColumn.id) return;

    void moveJob(jobId, targetColumn.id).catch((error: unknown) => {
      showToast(error instanceof Error ? error.message : 'Could not move the job', 'error');
    });
  };

  const handleJobClick = (job: Job) => {
    setSelectedJob(job);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedJob(null);
  };

  const handleDeleteJob = async () => {
    if (!pendingDelete) return;
    const job = pendingDelete;
    setPendingDelete(null);
    try {
      await deleteJob(job.id);
      handleCloseModal();
      showToast(`Deleted ${job.companyName}`, 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Failed to delete the job', 'error');
    }
  };

  return (
    <>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <div className="lg:hidden mb-4">
          <label htmlFor="kanban-stage" className="sr-only">
            Pipeline stage
          </label>
          <select
            id="kanban-stage"
            value={mobileColumn}
            onChange={(event) => setMobileColumn(event.target.value as JobStatus)}
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          >
            {KANBAN_COLUMNS.map((column) => (
              <option key={column.id} value={column.id}>
                {column.title} ({getJobsByStatus(column.id).length})
              </option>
            ))}
          </select>
        </div>

        <div className="flex gap-4 overflow-x-auto pb-4 min-h-[calc(100vh-200px)]">
          {KANBAN_COLUMNS.map(column => {
            const jobs = getJobsByStatus(column.id);
            return (
              <KanbanColumn
                key={column.id}
                column={column}
                jobs={jobs}
                onJobClick={handleJobClick}
                className={column.id === mobileColumn ? '' : 'hidden lg:flex'}
              />
            );
          })}
        </div>

        <DragOverlay>
          {activeJob && <JobCard job={activeJob} isDragging />}
        </DragOverlay>
      </DndContext>

      <Modal
        isOpen={isModalOpen}
        onClose={handleCloseModal}
        title={selectedJob ? `Edit: ${selectedJob.companyName}` : 'Job Details'}
        size="lg"
      >
        {selectedJob && (
          <JobForm
            job={selectedJob}
            onSave={handleCloseModal}
            onDelete={() => setPendingDelete(selectedJob)}
          />
        )}
      </Modal>

      <ConfirmDialog
        isOpen={pendingDelete !== null}
        title="Delete this job?"
        message={
          pendingDelete
            ? `${pendingDelete.companyName} · ${pendingDelete.jobTitle} will be removed, along with its status history. This cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        destructive
        onConfirm={() => void handleDeleteJob()}
        onCancel={() => setPendingDelete(null)}
      />
    </>
  );
}
