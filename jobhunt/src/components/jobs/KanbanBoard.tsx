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
import { JobForm } from './JobForm';

export function KanbanBoard() {
  const { state, moveJob, deleteJob, getJobsByStatus } = useJobs();
  const [activeJob, setActiveJob] = useState<Job | null>(null);
  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

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

    void moveJob(jobId, targetColumn.id);
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
    if (!selectedJob || !window.confirm('Are you sure you want to delete this job?')) {
      return;
    }
    try {
      await deleteJob(selectedJob.id);
      handleCloseModal();
    } catch (err) {
      window.alert(err instanceof Error ? err.message : 'Failed to delete the job');
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
        <div className="flex gap-4 overflow-x-auto pb-4 min-h-[calc(100vh-200px)]">
          {KANBAN_COLUMNS.map(column => {
            const jobs = getJobsByStatus(column.id);
            return (
              <KanbanColumn
                key={column.id}
                column={column}
                jobs={jobs}
                onJobClick={handleJobClick}
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
            onDelete={handleDeleteJob}
          />
        )}
      </Modal>
    </>
  );
}
