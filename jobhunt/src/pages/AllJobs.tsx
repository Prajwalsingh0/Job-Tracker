import { useCallback, useEffect, useState } from 'react';
import { useJobs } from '@/context/JobContext';
import { api, exportJobsCsv } from '@/lib/api';
import { Job, JobSortField, JobStatus, PageResponse, STATUS_LABELS } from '@/types';
import { Modal } from '@/components/ui/Modal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { JobForm } from '@/components/jobs/JobForm';
import {
  Search, Plus, ExternalLink, MapPin, Calendar, FileText, Filter,
  Download, ChevronLeft, ChevronRight, ArrowUp, ArrowDown, Loader2,
} from 'lucide-react';

const PAGE_SIZE = 10;

interface SortableHeaderProps {
  label: string;
  field: JobSortField;
  activeField: JobSortField;
  direction: 'asc' | 'desc';
  onSort: (field: JobSortField) => void;
  className?: string;
}

function SortableHeader({ label, field, activeField, direction, onSort, className = '' }: SortableHeaderProps) {
  const isActive = field === activeField;
  return (
    <button
      type="button"
      onClick={() => onSort(field)}
      aria-label={`Sort by ${label}, currently ${isActive ? direction : 'unsorted'}`}
      className={`flex items-center gap-1 text-xs font-semibold uppercase tracking-wide transition-colors ${
        isActive ? 'text-indigo-600' : 'text-gray-500 hover:text-gray-800'
      } ${className}`}
    >
      {label}
      {isActive && (direction === 'asc' ? <ArrowUp className="w-3.5 h-3.5" /> : <ArrowDown className="w-3.5 h-3.5" />)}
    </button>
  );
}

export function AllJobs() {
  const { deleteJob, getResumeById } = useJobs();

  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [sortField, setSortField] = useState<JobSortField>('updatedAt');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('desc');
  const [page, setPage] = useState(0);

  const [result, setResult] = useState<PageResponse<Job> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  // Debounce the free-text search so every keystroke does not hit the API.
  useEffect(() => {
    const timer = setTimeout(() => {
      setSearch(searchInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const loadJobs = useCallback(async () => {
    setIsLoading(true);
    try {
      const pageResult = await api.listJobs({
        search: search || undefined,
        status: statusFilter === 'all' ? undefined : (statusFilter as JobStatus),
        page,
        size: PAGE_SIZE,
        sort: sortField,
        direction: sortDirection,
      });

      // Deleting the last row of the last page can leave the client past the end.
      if (pageResult.content.length === 0 && page > 0) {
        setPage((current) => Math.max(current - 1, 0));
        return;
      }

      setResult(pageResult);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load jobs');
    } finally {
      setIsLoading(false);
    }
  }, [search, statusFilter, page, sortField, sortDirection]);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  const handleSort = (field: JobSortField) => {
    if (field === sortField) {
      setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
    setPage(0);
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      const { blob, fileName } = await exportJobsCsv({
        search: search || undefined,
        status: statusFilter === 'all' ? undefined : (statusFilter as JobStatus),
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to export jobs');
    } finally {
      setIsExporting(false);
    }
  };

  const handleJobClick = (job: Job) => {
    setSelectedJob(job);
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setSelectedJob(null);
  };

  const handleJobSaved = () => {
    handleCloseModal();
    void loadJobs();
  };

  const handleDeleteJob = async () => {
    if (!selectedJob || !window.confirm('Are you sure you want to delete this job?')) {
      return;
    }
    try {
      await deleteJob(selectedJob.id);
      handleCloseModal();
      await loadJobs();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete the job');
    }
  };

  const handleAddSaved = () => {
    setIsAddModalOpen(false);
    void loadJobs();
  };

  const getDaysSince = (date?: string) => {
    if (!date) return null;
    const start = new Date(date);
    const now = new Date();
    return Math.floor((now.getTime() - start.getTime()) / (1000 * 60 * 60 * 24));
  };

  const jobs = result?.content ?? [];
  const totalElements = result?.totalElements ?? 0;
  const totalPages = result?.totalPages ?? 0;
  const isFiltered = Boolean(search) || statusFilter !== 'all';

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">All Jobs</h1>
          <p className="text-gray-500 mt-1">
            {totalElements} {totalElements === 1 ? 'job' : 'jobs'} tracked
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleExport}
            disabled={isExporting || totalElements === 0}
            className="flex items-center gap-2 px-4 py-2 border border-gray-300 bg-white text-gray-700 rounded-lg hover:bg-gray-50 transition-colors shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isExporting ? <Loader2 className="w-5 h-5 animate-spin" /> : <Download className="w-5 h-5" />}
            {isExporting ? 'Exporting...' : 'Export CSV'}
          </button>
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
          >
            <Plus className="w-5 h-5" />
            Add Job
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-red-50 text-red-600 p-3 rounded-lg text-sm">{error}</div>
      )}

      {/* Search and Filter */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-gray-400" />
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by company, title, or location..."
            aria-label="Search jobs"
            className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter className="w-5 h-5 text-gray-400" />
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage(0);
            }}
            aria-label="Filter by status"
            className="px-4 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          >
            <option value="all">All Statuses</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Sort bar */}
      <div className="flex flex-wrap items-center gap-4 px-1">
        <SortableHeader label="Company" field="companyName" activeField={sortField} direction={sortDirection} onSort={handleSort} />
        <SortableHeader label="Title" field="jobTitle" activeField={sortField} direction={sortDirection} onSort={handleSort} />
        <SortableHeader label="Status" field="status" activeField={sortField} direction={sortDirection} onSort={handleSort} />
        <SortableHeader label="Applied" field="appliedDate" activeField={sortField} direction={sortDirection} onSort={handleSort} />
        <SortableHeader label="Updated" field="updatedAt" activeField={sortField} direction={sortDirection} onSort={handleSort} className="ml-auto" />
      </div>

      {/* Jobs List */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="px-6 py-16 text-center text-gray-400 text-sm">Loading jobs...</div>
        ) : jobs.length > 0 ? (
          <div className="divide-y divide-gray-100">
            {jobs.map((job) => {
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
                        <div className="flex items-center gap-4 mt-2 text-sm text-gray-500 flex-wrap">
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
                      <StatusBadge status={job.status} />
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
              {isFiltered ? 'Try adjusting your search or filters' : 'Add your first job to get started!'}
            </p>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-3 border-t border-gray-200 bg-gray-50">
            <span className="text-sm text-gray-500">
              Page {page + 1} of {totalPages}
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
                disabled={page === 0 || isLoading}
                aria-label="Previous page"
                className="flex items-center gap-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <ChevronLeft className="w-4 h-4" />
                Previous
              </button>
              <button
                onClick={() => setPage((current) => current + 1)}
                disabled={page + 1 >= totalPages || isLoading}
                aria-label="Next page"
                className="flex items-center gap-1 px-3 py-1.5 text-sm border border-gray-300 rounded-lg bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                Next
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
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
            onSave={handleJobSaved}
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
        <JobForm onSave={handleAddSaved} />
      </Modal>
    </div>
  );
}
