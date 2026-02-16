import { useState } from 'react';
import { KanbanBoard } from '@/components/jobs/KanbanBoard';
import { Modal } from '@/components/ui/Modal';
import { JobForm } from '@/components/jobs/JobForm';
import { Plus } from 'lucide-react';

export function Pipeline() {
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Pipeline</h1>
          <p className="text-gray-500 mt-1">Drag and drop jobs between stages</p>
        </div>
        <button
          onClick={() => setIsAddModalOpen(true)}
          className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
        >
          <Plus className="w-5 h-5" />
          Add Job
        </button>
      </div>

      {/* Kanban Board */}
      <KanbanBoard />

      {/* Add Job Modal */}
      <Modal
        isOpen={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        title="Add New Job"
        size="lg"
      >
        <JobForm onSave={() => setIsAddModalOpen(false)} />
      </Modal>
    </div>
  );
}
