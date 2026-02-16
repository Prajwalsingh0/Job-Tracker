import { Job } from '@/types';
import { MapPin, Calendar, ExternalLink, FileText } from 'lucide-react';
import { useJobs } from '@/context/JobContext';

interface JobCardProps {
  job: Job;
  onClick?: () => void;
  isDragging?: boolean;
}

export function JobCard({ job, onClick, isDragging = false }: JobCardProps) {
  const { getResumeById } = useJobs();
  const resume = job.resumeId ? getResumeById(job.resumeId) : undefined;

  const getDaysSince = (date?: string) => {
    if (!date) return null;
    const start = new Date(date);
    const now = new Date();
    const diff = Math.floor((now.getTime() - start.getTime()) / (1000 * 60 * 60 * 24));
    return diff;
  };

  const daysSinceApplied = getDaysSince(job.appliedDate);
  const daysSinceCreated = getDaysSince(job.createdAt);

  return (
    <div
      onClick={onClick}
      className={`bg-white rounded-lg border border-gray-200 p-4 cursor-pointer
        hover:shadow-md hover:border-gray-300 transition-all duration-200
        ${isDragging ? 'shadow-lg rotate-2 scale-105' : ''}`}
    >
      <div className="mb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white font-semibold text-sm">
              {job.companyName.charAt(0).toUpperCase()}
            </div>
            <div>
              <h3 className="font-semibold text-gray-900 text-sm leading-tight">
                {job.companyName}
              </h3>
              <p className="text-gray-600 text-xs">{job.jobTitle}</p>
            </div>
          </div>
          {job.jobUrl && (
            <a
              href={job.jobUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={e => e.stopPropagation()}
              className="p-1.5 text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 rounded transition-colors"
            >
              <ExternalLink className="w-4 h-4" />
            </a>
          )}
        </div>
      </div>

      {job.location && (
        <div className="flex items-center gap-1.5 text-gray-500 text-xs mb-2">
          <MapPin className="w-3.5 h-3.5" />
          <span>{job.location}</span>
        </div>
      )}

      <div className="flex items-center gap-3 flex-wrap">
        {job.status !== 'wishlist' && daysSinceApplied !== null && (
          <div className="flex items-center gap-1 text-xs text-gray-500">
            <Calendar className="w-3.5 h-3.5" />
            <span>{daysSinceApplied}d ago</span>
          </div>
        )}

        {job.status === 'wishlist' && daysSinceCreated !== null && (
          <div className="flex items-center gap-1 text-xs text-gray-500">
            <Calendar className="w-3.5 h-3.5" />
            <span>Added {daysSinceCreated}d ago</span>
          </div>
        )}

        {resume && (
          <div className="flex items-center gap-1 text-xs text-indigo-600">
            <FileText className="w-3.5 h-3.5" />
            <span>{resume.name}</span>
          </div>
        )}
      </div>

      {job.salaryRange && (
        <div className="mt-2 text-xs text-green-600 font-medium">
          {job.salaryRange}
        </div>
      )}

      {job.status === 'rejected' && job.feedback && (
        <div className="mt-2 text-xs text-gray-500 bg-gray-50 p-2 rounded line-clamp-2">
          {job.feedback}
        </div>
      )}
    </div>
  );
}
