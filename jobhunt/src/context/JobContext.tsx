import React, { createContext, useContext, useReducer, useEffect, ReactNode } from 'react';
import { Job, Resume, JobStats, JobStatus } from '../types';
import { v4 as uuidv4 } from 'uuid';

// State interface
interface JobState {
  jobs: Job[];
  resumes: Resume[];
  isLoading: boolean;
}

// Action types
type JobAction =
  | { type: 'SET_JOBS'; payload: Job[] }
  | { type: 'ADD_JOB'; payload: Job }
  | { type: 'UPDATE_JOB'; payload: Job }
  | { type: 'DELETE_JOB'; payload: string }
  | { type: 'MOVE_JOB'; payload: { jobId: string; newStatus: JobStatus } }
  | { type: 'SET_RESUMES'; payload: Resume[] }
  | { type: 'ADD_RESUME'; payload: Resume }
  | { type: 'DELETE_RESUME'; payload: string }
  | { type: 'SET_LOADING'; payload: boolean };

// Initial state
const initialState: JobState = {
  jobs: [],
  resumes: [],
  isLoading: true,
};

// Reducer
function jobReducer(state: JobState, action: JobAction): JobState {
  switch (action.type) {
    case 'SET_JOBS':
      return { ...state, jobs: action.payload, isLoading: false };
    case 'ADD_JOB':
      return { ...state, jobs: [...state.jobs, action.payload] };
    case 'UPDATE_JOB':
      return {
        ...state,
        jobs: state.jobs.map(job =>
          job.id === action.payload.id ? action.payload : job
        ),
      };
    case 'DELETE_JOB':
      return {
        ...state,
        jobs: state.jobs.filter(job => job.id !== action.payload),
      };
    case 'MOVE_JOB':
      return {
        ...state,
        jobs: state.jobs.map(job =>
          job.id === action.payload.jobId
            ? {
                ...job,
                status: action.payload.newStatus,
                updatedAt: new Date().toISOString(),
                appliedDate: action.payload.newStatus === 'applied' && !job.appliedDate
                  ? new Date().toISOString().split('T')[0]
                  : job.appliedDate
              }
            : job
        ),
      };
    case 'SET_RESUMES':
      return { ...state, resumes: action.payload };
    case 'ADD_RESUME':
      return { ...state, resumes: [...state.resumes, action.payload] };
    case 'DELETE_RESUME':
      return {
        ...state,
        resumes: state.resumes.filter(resume => resume.id !== action.payload),
      };
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    default:
      return state;
  }
}

// Context interface
interface JobContextType {
  state: JobState;
  // Job actions
  addJob: (job: Omit<Job, 'id' | 'createdAt' | 'updatedAt' | 'interviews'>) => void;
  updateJob: (job: Job) => void;
  deleteJob: (jobId: string) => void;
  moveJob: (jobId: string, newStatus: JobStatus) => void;
  getJobById: (jobId: string) => Job | undefined;
  // Resume actions
  addResume: (resume: Omit<Resume, 'id' | 'createdAt'>) => void;
  deleteResume: (resumeId: string) => void;
  getResumeById: (resumeId: string) => Resume | undefined;
  // Stats
  getStats: () => JobStats;
  // Filtering
  getJobsByStatus: (status: JobStatus) => Job[];
  searchJobs: (query: string) => Job[];
}

const JobContext = createContext<JobContextType | undefined>(undefined);

// Storage keys
const JOBS_STORAGE_KEY = 'jobhunt_jobs';
const RESUMES_STORAGE_KEY = 'jobhunt_resumes';

// Provider component
export function JobProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(jobReducer, initialState);

  // Load data from localStorage on mount
  useEffect(() => {
    try {
      const storedJobs = localStorage.getItem(JOBS_STORAGE_KEY);
      const storedResumes = localStorage.getItem(RESUMES_STORAGE_KEY);

      if (storedJobs) {
        dispatch({ type: 'SET_JOBS', payload: JSON.parse(storedJobs) });
      } else {
        dispatch({ type: 'SET_LOADING', payload: false });
      }

      if (storedResumes) {
        dispatch({ type: 'SET_RESUMES', payload: JSON.parse(storedResumes) });
      }
    } catch (error) {
      console.error('Error loading data from localStorage:', error);
      dispatch({ type: 'SET_LOADING', payload: false });
    }
  }, []);

  // Save jobs to localStorage whenever they change
  useEffect(() => {
    if (!state.isLoading) {
      localStorage.setItem(JOBS_STORAGE_KEY, JSON.stringify(state.jobs));
    }
  }, [state.jobs, state.isLoading]);

  // Save resumes to localStorage whenever they change
  useEffect(() => {
    if (!state.isLoading) {
      localStorage.setItem(RESUMES_STORAGE_KEY, JSON.stringify(state.resumes));
    }
  }, [state.resumes, state.isLoading]);

  // Job actions
  const addJob = (jobData: Omit<Job, 'id' | 'createdAt' | 'updatedAt' | 'interviews'>) => {
    const now = new Date().toISOString();
    const newJob: Job = {
      ...jobData,
      id: uuidv4(),
      interviews: [],
      createdAt: now,
      updatedAt: now,
    };
    dispatch({ type: 'ADD_JOB', payload: newJob });
  };

  const updateJob = (job: Job) => {
    const updatedJob = { ...job, updatedAt: new Date().toISOString() };
    dispatch({ type: 'UPDATE_JOB', payload: updatedJob });
  };

  const deleteJob = (jobId: string) => {
    dispatch({ type: 'DELETE_JOB', payload: jobId });
  };

  const moveJob = (jobId: string, newStatus: JobStatus) => {
    dispatch({ type: 'MOVE_JOB', payload: { jobId, newStatus } });
  };

  const getJobById = (jobId: string) => {
    return state.jobs.find(job => job.id === jobId);
  };

  // Resume actions
  const addResume = (resumeData: Omit<Resume, 'id' | 'createdAt'>) => {
    const newResume: Resume = {
      ...resumeData,
      id: uuidv4(),
      createdAt: new Date().toISOString(),
    };
    dispatch({ type: 'ADD_RESUME', payload: newResume });
  };

  const deleteResume = (resumeId: string) => {
    dispatch({ type: 'DELETE_RESUME', payload: resumeId });
  };

  const getResumeById = (resumeId: string) => {
    return state.resumes.find(resume => resume.id === resumeId);
  };

  // Get stats
  const getStats = (): JobStats => {
    const jobs = state.jobs;
    const total = jobs.length;
    const wishlist = jobs.filter(j => j.status === 'wishlist').length;
    const applied = jobs.filter(j => j.status !== 'wishlist').length;
    const interviewing = jobs.filter(j =>
      ['phone_screen', 'interview'].includes(j.status)
    ).length;
    const offers = jobs.filter(j => j.status === 'offer').length;
    const rejected = jobs.filter(j => j.status === 'rejected').length;

    const responded = jobs.filter(j =>
      !['wishlist', 'applied', 'ghosted'].includes(j.status)
    ).length;

    const responseRate = applied > 0 ? (responded / applied) * 100 : 0;
    const interviewRate = applied > 0 ? (interviewing / applied) * 100 : 0;

    return {
      total,
      wishlist,
      applied,
      interviewing,
      offers,
      rejected,
      responseRate,
      interviewRate,
    };
  };

  // Filtering
  const getJobsByStatus = (status: JobStatus) => {
    return state.jobs.filter(job => job.status === status);
  };

  const searchJobs = (query: string) => {
    const lowerQuery = query.toLowerCase();
    return state.jobs.filter(job =>
      job.companyName.toLowerCase().includes(lowerQuery) ||
      job.jobTitle.toLowerCase().includes(lowerQuery) ||
      job.location?.toLowerCase().includes(lowerQuery)
    );
  };

  const value: JobContextType = {
    state,
    addJob,
    updateJob,
    deleteJob,
    moveJob,
    getJobById,
    addResume,
    deleteResume,
    getResumeById,
    getStats,
    getJobsByStatus,
    searchJobs,
  };

  return <JobContext.Provider value={value}>{children}</JobContext.Provider>;
}

// Hook to use the context
export function useJobs() {
  const context = useContext(JobContext);
  if (context === undefined) {
    throw new Error('useJobs must be used within a JobProvider');
  }
  return context;
}
