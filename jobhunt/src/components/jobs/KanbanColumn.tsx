import { useDroppable } from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { Job, KanbanColumn as KanbanColumnType } from '@/types';
import { SortableJobCard } from './SortableJobCard';

interface KanbanColumnProps {
  column: KanbanColumnType;
  jobs: Job[];
  onJobClick: (job: Job) => void;
  /** Lets the board hide columns on small screens when a single stage is selected. */
  className?: string;
}

export function KanbanColumn({ column, jobs, onJobClick, className = '' }: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({
    id: column.id,
  });

  return (
    <div
      ref={setNodeRef}
      className={`flex-shrink-0 w-full lg:w-72 bg-gray-50 rounded-xl p-3 flex flex-col transition-colors
        ${isOver ? 'bg-indigo-50 ring-2 ring-indigo-300' : ''} ${className}`}
    >
      <div className="flex items-center justify-between mb-3 px-1">
        <div className="flex items-center gap-2">
          <div className={`w-3 h-3 rounded-full ${column.color}`} />
          <h3 className="font-semibold text-gray-800">{column.title}</h3>
        </div>
        <span className="text-sm text-gray-500 bg-gray-200 px-2 py-0.5 rounded-full">
          {jobs.length}
        </span>
      </div>

      <div className="flex-1 overflow-y-auto space-y-3 min-h-[100px]">
        <SortableContext
          items={jobs.map(j => j.id)}
          strategy={verticalListSortingStrategy}
        >
          {jobs.map(job => (
            <SortableJobCard
              key={job.id}
              job={job}
              onClick={() => onJobClick(job)}
            />
          ))}
        </SortableContext>

        {jobs.length === 0 && (
          <div className="text-center py-8 text-gray-400 text-sm">
            Drop jobs here
          </div>
        )}
      </div>
    </div>
  );
}
