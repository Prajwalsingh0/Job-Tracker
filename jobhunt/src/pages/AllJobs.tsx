import { useState } from 'react';
import { useJobs } from '@/context/JobContext';
import { Job, STATUS_LABELS, STATUS_COLORS } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { JobForm } from '@/components/jobs/JobForm';
import { Search, Plus, ExternalLink, MapPin, Calendar, FileText, Filter } from 'lucide-react';

export function AllJobs() {
  const { state, searchJobs, deleteJob, getResumeById } = useJobs();
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  const filteredJobs = (searchQuery ? searchJobs(searchQuery) : state.jobs)
    .filter(job => statusFilter === 'all' || job.status === statusFilter)
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());

  const handleJobClick = (job: Job) => {
    setSelectedJob(job);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedJob(null);
  };

  const handleDeleteJob = () => {
    if (selectedJob && window.confirm('Are you sure you want to delete this job?')) {
      deleteJob(selectedJob.id);
      handleCloseModal();
    }
  };

  const getDaysSince = (date?: string) => {
    if (!date) return null;
    const start = new Date(date);
    const now = new Date();
    return Math.floor((now.getTime() - start.getTime()) / (1000 * 60 * 60 * 24));
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">All Jobs</h1>
          <p className="text-gray-500 mt-1">{filteredJobs.length} jobs tracked</p>
        </div>
        <button
          onClick={() => setIsAddModalOpen(true)}
          className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
        >
          <Plus className="w-5 h-5" />
          Add Job
        </button>
      </div>

      {/* Search and Filter */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by company, title, or location..."
            className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter className="w-5 h-5 text-gray-400" />
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          >
            <option value="all">All Statuses</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Jobs List */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {filteredJobs.length > 0 ? (
          <div className="divide-y divide-gray-100">
            {filteredJobs.map((job) => {
              const resume = job.resumeId ? getResumeById(job.resumeId) : undefined;
              const daysSince = getDaysSince(job.appliedDate || job.createdAt);

              return (
                <div
                  key={job.id}
                  onClick={() => handleJobClick(job)}
                  className="px-6 py-4 hover:bg-gray-50 transition-colors cursor-pointer"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex items-start gap-4 flex-1 min-w-0">
                      <div className="w-12 h-12 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white font-semibold text-lg flex-shrink-0">
                        {job.companyName.charAt(0).toUpperCase()}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <h3 className="font-semibold text-gray-900 truncate">{job.companyName}</h3>
                          {job.jobUrl && (
                            <a
                              href={job.jobUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              onClick={(e) => e.stopPropagation()}
                              className="text-gray-400 hover:text-indigo-600 transition-colors"
                            >
                              <ExternalLink className="w-4 h-4" />
                            </a>
                          )}
                        </div>
                        <p className="text-gray-600">{job.jobTitle}</p>
                        <div className="flex items-center gap-4 mt-2 text-sm text-gray-500">
                          {job.location && (
                            <span className="flex items-center gap-1">
                              <MapPin className="w-4 h-4" />
                              {job.location}
                            </span>
                          )}
                          {daysSince !== null && (
                            <span className="flex items-center gap-1">
                              <Calendar className="w-4 h-4" />
                              {daysSince}d ago
                            </span>
                          )}
                          {resume && (
                            <span className="flex items-center gap-1 text-indigo-600">
                              <FileText className="w-4 h-4" />
                              {resume.name}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="flex flex-col items-end gap-2 flex-shrink-0">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${STATUS_COLORS[job.status]}`}>
                        {STATUS_LABELS[job.status]}
                      </span>
                      {job.salaryRange && (
                        <span className="text-sm text-green-600 font-medium">{job.salaryRange}</span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="px-6 py-16 text-center">
            <div className="w-16 h-16 mx-auto mb-4 bg-gray-100 rounded-full flex items-center justify-center">
              <Search className="w-8 h-8 text-gray-400" />
            </div>
            <p className="text-gray-600 font-medium">No jobs found</p>
            <p className="text-gray-400 text-sm mt-1">
              {searchQuery || statusFilter !== 'all'
                ? 'Try adjusting your search or filters'
                : 'Add your first job to get started!'}
            </p>
          </div>
        )}
      </div>

      {/* Edit Job Modal */}
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
