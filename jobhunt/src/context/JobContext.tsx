import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react';
import { api } from '@/lib/api';
import { useAuth } from './AuthContext';
import { Job, JobPayload, JobStats, JobStatus, Resume } from '../types';

interface JobState {
  jobs: Job[];
  resumes: Resume[];
  stats: JobStats | null;
  isLoading: boolean;
  error: string | null;
}

interface JobContextType {
  state: JobState;
  refresh: () => Promise<void>;
  // Job actions
  addJob: (payload: JobPayload) => Promise<Job>;
  updateJob: (jobId: number, payload: JobPayload) => Promise<Job>;
  deleteJob: (jobId: number) => Promise<void>;
  moveJob: (jobId: number, status: JobStatus) => Promise<Job>;
  getJobById: (jobId: number) => Job | undefined;
  // Resume actions
  addResume: (file: File, meta?: { name?: string; versionTag?: string }) => Promise<Resume>;
  deleteResume: (resumeId: number) => Promise<void>;
  getResumeById: (resumeId: number) => Resume | undefined;
  // Local selectors
  getJobsByStatus: (status: JobStatus) => Job[];
}

const JobContext = createContext<JobContextType | undefined>(undefined);

export function JobProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();

  const [jobs, setJobs] = useState<Job[]>([]);
  const [resumes, setResumes] = useState<Resume[]>([]);
  const [stats, setStats] = useState<JobStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    const [jobsResult, resumesResult, statsResult] = await Promise.all([
      api.listJobs(),
      api.listResumes(),
      api.jobStats(),
    ]);
    setJobs(jobsResult);
    setResumes(resumesResult);
    setStats(statsResult);
  }, []);

  const refresh = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      await loadAll();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load your data');
    } finally {
      setIsLoading(false);
    }
  }, [loadAll]);

  // Load data once the user is authenticated; clear it on sign-out.
  useEffect(() => {
    if (!user) {
      setJobs([]);
      setResumes([]);
      setStats(null);
      setIsLoading(false);
      setError(null);
      return;
    }
    void refresh();
  }, [user, refresh]);

  /** Runs a mutation, then re-syncs jobs/stats/resumes from the server. */
  const mutate = useCallback(
    async <T,>(action: () => Promise<T>): Promise<T> => {
      const result = await action();
      try {
        await loadAll();
      } catch {
        // The mutation succeeded; a failed refresh should not fail the action.
      }
      return result;
    },
    [loadAll],
  );

  const addJob = useCallback(
    (payload: JobPayload) => mutate(() => api.createJob(payload)),
    [mutate],
  );

  const updateJob = useCallback(
    (jobId: number, payload: JobPayload) => mutate(() => api.updateJob(jobId, payload)),
    [mutate],
  );

  const deleteJob = useCallback(
    (jobId: number) => mutate(() => api.deleteJob(jobId)),
    [mutate],
  );

  const moveJob = useCallback(
    (jobId: number, status: JobStatus) => mutate(() => api.updateJobStatus(jobId, status)),
    [mutate],
  );

  const addResume = useCallback(
    (file: File, meta: { name?: string; versionTag?: string } = {}) =>
      mutate(() => api.uploadResume(file, meta)),
    [mutate],
  );

  const deleteResume = useCallback(
    (resumeId: number) => mutate(() => api.deleteResume(resumeId)),
    [mutate],
  );

  const getJobById = useCallback(
    (jobId: number) => jobs.find((job) => job.id === jobId),
    [jobs],
  );

  const getResumeById = useCallback(
    (resumeId: number) => resumes.find((resume) => resume.id === resumeId),
    [resumes],
  );

  const getJobsByStatus = useCallback(
    (status: JobStatus) => jobs.filter((job) => job.status === status),
    [jobs],
  );

  const value: JobContextType = {
    state: { jobs, resumes, stats, isLoading, error },
    refresh,
    addJob,
    updateJob,
    deleteJob,
    moveJob,
    getJobById,
    addResume,
    deleteResume,
    getResumeById,
    getJobsByStatus,
  };

  return <JobContext.Provider value={value}>{children}</JobContext.Provider>;
}

export function useJobs() {
  const context = useContext(JobContext);
  if (context === undefined) {
    throw new Error('useJobs must be used within a JobProvider');
  }
  return context;
}
