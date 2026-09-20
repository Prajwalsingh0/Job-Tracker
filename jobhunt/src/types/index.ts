// Job Application Types for JobHunt
// These mirror the payloads returned by the Spring Boot backend (see backend/src/main/java/com/jobhunt/dto).

export type JobStatus =
  | 'wishlist'
  | 'applied'
  | 'phone_screen'
  | 'interview'
  | 'offer'
  | 'rejected'
  | 'withdrawn'
  | 'ghosted';

export type InterviewType = 'phone' | 'video' | 'onsite' | 'technical' | 'behavioral';

export type OutcomeReason =
  | 'position_filled'
  | 'not_qualified'
  | 'culture_fit'
  | 'salary_mismatch'
  | 'other';

export type JobOutcome = 'offer' | 'rejected' | 'withdrawn' | 'ghosted';

export type ResumeFileType = 'pdf' | 'docx';

/** Where the work happens. */
export type WorkMode = 'remote' | 'hybrid' | 'onsite';

/** One recorded pipeline transition for a job. */
export interface JobStatusHistoryEntry {
  id: number;
  jobId: number;
  jobCompanyName?: string;
  jobTitle?: string;
  fromStatus?: JobStatus;
  toStatus: JobStatus;
  changedAt: string;
  note?: string;
}

/** Resume metadata. The document itself is fetched from /api/resumes/{id}/download. */
export interface Resume {
  id: number;
  name: string;
  fileName: string;
  fileType: ResumeFileType;
  versionTag?: string;
  usageCount: number;
  createdAt: string;
}

export interface Interview {
  id: number;
  jobId: number;
  date: string;
  type: InterviewType;
  interviewerName?: string;
  notes?: string;
  createdAt: string;
}

/** A cover letter is an uploaded document, pasted text, or both. */
export interface CoverLetter {
  id: number;
  name: string;
  jobId?: number;
  body?: string;
  fileName?: string;
  fileType?: ResumeFileType;
  fileSize?: number;
  versionTag?: string;
  createdAt: string;
}

export interface Job {
  id: number;
  companyName: string;
  jobTitle: string;
  jobUrl?: string;
  description?: string;
  location?: string;
  salaryRange?: string;
  status: JobStatus;
  appliedDate?: string;
  targetApplyDate?: string;
  outcome?: JobOutcome;
  outcomeReason?: OutcomeReason;
  feedback?: string;
  notes?: string;
  jobSource?: string;
  workMode?: WorkMode;
  deadline?: string;
  salaryMin?: number;
  salaryMax?: number;
  salaryCurrency?: string;
  tags?: string[];
  resumeId?: number;
  interviews?: Interview[];
  createdAt: string;
  updatedAt: string;
}

/** Create/update payload for a job. `outcome` is derived from `status` by the backend. */
export interface JobPayload {
  companyName: string;
  jobTitle: string;
  jobUrl?: string;
  description?: string;
  location?: string;
  salaryRange?: string;
  status: JobStatus;
  appliedDate?: string;
  targetApplyDate?: string;
  outcomeReason?: OutcomeReason;
  feedback?: string;
  notes?: string;
  jobSource?: string;
  workMode?: WorkMode;
  deadline?: string;
  salaryMin?: number;
  salaryMax?: number;
  salaryCurrency?: string;
  tags?: string[];
  resumeId?: number | null;
}

export interface JobStats {
  total: number;
  wishlist: number;
  applied: number;
  interviewing: number;
  offers: number;
  rejected: number;
  responseRate: number;
  interviewRate: number;
}

/** Pagination envelope returned by list endpoints. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Client-facing sort keys accepted by GET /api/jobs. */
export type JobSortField =
  | 'updatedAt'
  | 'createdAt'
  | 'companyName'
  | 'jobTitle'
  | 'status'
  | 'appliedDate'
  | 'targetApplyDate';

// Column configuration for Kanban board
export interface KanbanColumn {
  id: JobStatus;
  title: string;
  color: string;
}

export const KANBAN_COLUMNS: KanbanColumn[] = [
  { id: 'wishlist', title: 'Wishlist', color: 'bg-slate-500' },
  { id: 'applied', title: 'Applied', color: 'bg-blue-500' },
  { id: 'phone_screen', title: 'Phone Screen', color: 'bg-cyan-500' },
  { id: 'interview', title: 'Interview', color: 'bg-purple-500' },
  { id: 'offer', title: 'Offer', color: 'bg-green-500' },
  { id: 'rejected', title: 'Rejected', color: 'bg-red-500' },
  { id: 'withdrawn', title: 'Withdrawn', color: 'bg-orange-500' },
  { id: 'ghosted', title: 'Ghosted', color: 'bg-gray-500' },
];

export const STATUS_LABELS: Record<JobStatus, string> = {
  wishlist: 'Wishlist',
  applied: 'Applied',
  phone_screen: 'Phone Screen',
  interview: 'Interview',
  offer: 'Offer',
  rejected: 'Rejected',
  withdrawn: 'Withdrawn',
  ghosted: 'Ghosted',
};

export const STATUS_COLORS: Record<JobStatus, string> = {
  wishlist: 'bg-slate-100 text-slate-700 border-slate-300',
  applied: 'bg-blue-100 text-blue-700 border-blue-300',
  phone_screen: 'bg-cyan-100 text-cyan-700 border-cyan-300',
  interview: 'bg-purple-100 text-purple-700 border-purple-300',
  offer: 'bg-green-100 text-green-700 border-green-300',
  rejected: 'bg-red-100 text-red-700 border-red-300',
  withdrawn: 'bg-orange-100 text-orange-700 border-orange-300',
  ghosted: 'bg-gray-100 text-gray-700 border-gray-300',
};
