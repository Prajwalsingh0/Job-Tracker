import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { Job } from '@/types';
import { JobCard } from './JobCard';

interface SortableJobCardProps {
  job: Job;
  onClick: () => void;
}

export function SortableJobCard({ job, onClick }: SortableJobCardProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: job.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <JobCard job={job} onClick={onClick} isDragging={isDragging} />
    </div>
  );
}
