import { useState, useEffect } from 'react';
import { Job, JobPayload, JobStatus, JobStatusHistoryEntry, OutcomeReason, STATUS_LABELS, WorkMode } from '@/types';
import { useJobs } from '@/context/JobContext';
import { api } from '@/lib/api';
import { Building2, Briefcase, Link, MapPin, DollarSign, FileText, Calendar, Save, Trash2, Upload, Tags, History } from 'lucide-react';

interface JobFormProps {
  job?: Job;
  initialStatus?: JobStatus;
  onSave: () => void;
  onDelete?: () => void;
}

export function JobForm({ job, initialStatus = 'wishlist', onSave, onDelete }: JobFormProps) {
  const { addJob, updateJob, state, addResume } = useJobs();

  const [formData, setFormData] = useState({
    companyName: '',
    jobTitle: '',
    jobUrl: '',
    description: '',
    location: '',
    salaryRange: '',
    status: initialStatus as JobStatus,
    appliedDate: new Date().toISOString().split('T')[0],
    targetApplyDate: '',
    outcomeReason: null as OutcomeReason | null,
    feedback: '',
    notes: '',
    resumeId: '',
    workMode: '',
    deadline: '',
    jobSource: '',
    salaryMin: '',
    salaryMax: '',
    salaryCurrency: 'USD',
    tagsInput: '',
  });

  const [resumeFile, setResumeFile] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const [history, setHistory] = useState<JobStatusHistoryEntry[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);

  // The pipeline timeline only makes sense for a job that already exists.
  useEffect(() => {
    if (!job) {
      setHistory([]);
      return;
    }

    let cancelled = false;
    setIsLoadingHistory(true);

    api.jobHistory(job.id)
      .then((entries) => {
        if (!cancelled) setHistory(entries);
      })
      .catch(() => {
        if (!cancelled) setHistory([]);
      })
      .finally(() => {
        if (!cancelled) setIsLoadingHistory(false);
      });

    return () => {
      cancelled = true;
    };
  }, [job]);

  useEffect(() => {
    if (job) {
      setFormData({
        companyName: job.companyName,
        jobTitle: job.jobTitle,
        jobUrl: job.jobUrl || '',
        description: job.description || '',
        location: job.location || '',
        salaryRange: job.salaryRange || '',
        status: job.status,
        appliedDate: job.appliedDate || new Date().toISOString().split('T')[0],
        targetApplyDate: job.targetApplyDate || '',
        outcomeReason: job.outcomeReason ?? null,
        feedback: job.feedback || '',
        notes: job.notes || '',
        resumeId: job.resumeId ? String(job.resumeId) : '',
        workMode: job.workMode ?? '',
        deadline: job.deadline ?? '',
        jobSource: job.jobSource ?? '',
        salaryMin: job.salaryMin ? String(job.salaryMin) : '',
        salaryMax: job.salaryMax ? String(job.salaryMax) : '',
        salaryCurrency: job.salaryCurrency ?? 'USD',
        tagsInput: (job.tags ?? []).join(', '),
      });
    }
  }, [job]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleResumeUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
    if (!validTypes.includes(file.type)) {
      setSubmitError('Please upload a PDF or DOCX file');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      setSubmitError('File size must be less than 10MB');
      return;
    }

    setSubmitError(null);
    setResumeFile(file);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setSubmitError(null);

    try {
      // A document uploaded here is saved to the resume library and linked to this job
      // in a single step, using the id returned by the API.
      let resumeId: number | null = formData.resumeId ? Number(formData.resumeId) : null;

      if (resumeFile) {
        const versionTag = [formData.companyName, formData.jobTitle].filter(Boolean).join(' - ');
        const created = await addResume(resumeFile, {
          name: resumeFile.name.replace(/\.[^/.]+$/, ''),
          versionTag: versionTag || undefined,
        });
        resumeId = created.id;
      }

      const payload: JobPayload = {
        companyName: formData.companyName,
        jobTitle: formData.jobTitle,
        jobUrl: formData.jobUrl || undefined,
        description: formData.description || undefined,
        location: formData.location || undefined,
        salaryRange: formData.salaryRange || undefined,
        status: formData.status,
        appliedDate: formData.status !== 'wishlist' ? formData.appliedDate : undefined,
        targetApplyDate: formData.targetApplyDate || undefined,
        outcomeReason: formData.outcomeReason || undefined,
        feedback: formData.feedback || undefined,
        notes: formData.notes || undefined,
        workMode: formData.workMode ? (formData.workMode as WorkMode) : undefined,
        deadline: formData.deadline || undefined,
        jobSource: formData.jobSource || undefined,
        salaryMin: formData.salaryMin ? Number(formData.salaryMin) : undefined,
        salaryMax: formData.salaryMax ? Number(formData.salaryMax) : undefined,
        salaryCurrency: formData.salaryCurrency || undefined,
        tags: formData.tagsInput.split(',').map((tag) => tag.trim()).filter(Boolean),
        resumeId,
      };

      if (job) {
        await updateJob(job.id, payload);
      } else {
        await addJob(payload);
      }

      onSave();
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Something went wrong while saving.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const showAppliedDate = formData.status !== 'wishlist';
  const showOutcome = ['offer', 'rejected', 'withdrawn', 'ghosted'].includes(formData.status);

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {submitError && (
        <div className="bg-red-50 text-red-600 p-3 rounded-lg text-sm">{submitError}</div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            <Building2 className="w-4 h-4 inline mr-1" />
            Company Name *
          </label>
          <input type="text" name="companyName" value={formData.companyName} onChange={handleChange} required
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="e.g. Google" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            <Briefcase className="w-4 h-4 inline mr-1" />
            Job Title *
          </label>
          <input type="text" name="jobTitle" value={formData.jobTitle} onChange={handleChange} required
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="e.g. Software Engineer" />
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            <Link className="w-4 h-4 inline mr-1" />
            Job URL
          </label>
          <input type="url" name="jobUrl" value={formData.jobUrl} onChange={handleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="https://..." />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            <MapPin className="w-4 h-4 inline mr-1" />
            Location
          </label>
          <input type="text" name="location" value={formData.location} onChange={handleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="e.g. San Francisco, CA" />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          <DollarSign className="w-4 h-4 inline mr-1" />
          Salary Range
        </label>
        <input type="text" name="salaryRange" value={formData.salaryRange} onChange={handleChange}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          placeholder="e.g. $120k - $150k" />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Work Mode</label>
          <select name="workMode" value={formData.workMode} onChange={handleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500">
            <option value="">Not specified</option>
            <option value="remote">Remote</option>
            <option value="hybrid">Hybrid</option>
            <option value="onsite">On-site</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Job Source</label>
          <input type="text" name="jobSource" value={formData.jobSource} onChange={handleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            placeholder="e.g. LinkedIn, referral" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            <Calendar className="w-4 h-4 inline mr-1" />
            Deadline
          </label>
          <input type="date" name="deadline" value={formData.deadline} onChange={handleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Base salary (optional)</label>
        <div className="flex items-center gap-2">
          <select name="salaryCurrency" value={formData.salaryCurrency} onChange={handleChange}
            aria-label="Salary currency"
            className="w-28 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500">
            {['USD', 'EUR', 'GBP', 'INR', 'CAD', 'AUD'].map((code) => (
              <option key={code} value={code}>{code}</option>
            ))}
          </select>
          <input type="number" min="0" name="salaryMin" value={formData.salaryMin} onChange={handleChange}
            aria-label="Minimum salary" placeholder="Min"
            className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
          <span className="text-gray-400">to</span>
          <input type="number" min="0" name="salaryMax" value={formData.salaryMax} onChange={handleChange}
            aria-label="Maximum salary" placeholder="Max"
            className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
        </div>
        <p className="mt-1 text-xs text-gray-400">Structured numbers in the selected currency; the free-text range above stays visible in lists.</p>
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          <Tags className="w-4 h-4 inline mr-1" />
          Tags
        </label>
        <input type="text" name="tagsInput" value={formData.tagsInput} onChange={handleChange}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          placeholder="Comma separated, e.g. referral, dream job, remote-friendly" />
        {formData.tagsInput.trim() && (
          <div className="flex flex-wrap gap-1.5 mt-2">
            {formData.tagsInput.split(',').map((tag) => tag.trim()).filter(Boolean).map((tag) => (
              <span key={tag} className="inline-flex items-center rounded-full bg-indigo-50 text-indigo-700 text-xs px-2 py-0.5">
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
          <select name="status" value={formData.status} onChange={handleChange}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500">
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </div>

        {showAppliedDate && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              <Calendar className="w-4 h-4 inline mr-1" />
              Applied Date
            </label>
            <input type="date" name="appliedDate" value={formData.appliedDate} onChange={handleChange}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
        )}

        {formData.status === 'wishlist' && (
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              <Calendar className="w-4 h-4 inline mr-1" />
              Target Apply Date
            </label>
            <input type="date" name="targetApplyDate" value={formData.targetApplyDate} onChange={handleChange}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500" />
          </div>
        )}
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">
          <FileText className="w-4 h-4 inline mr-1" />
          Resume
        </label>
        <div className="flex items-center gap-4">
          <select name="resumeId" value={formData.resumeId} onChange={handleChange}
            className="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500">
            <option value="">-- Select from library --</option>
            {state.resumes.map(resume => (
              <option key={resume.id} value={resume.id}>
                {resume.name} ({resume.versionTag || resume.fileType})
              </option>
            ))}
          </select>
          <label className="flex items-center gap-2 px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded-lg cursor-pointer transition-colors">
            <Upload className="w-4 h-4" />
            <span className="text-sm">Upload New</span>
            <input type="file" accept=".pdf,.docx" onChange={handleResumeUpload} className="hidden" />
          </label>
        </div>
        {resumeFile && (
          <p className="mt-2 text-sm text-green-600">
            Selected: {resumeFile.name} — it will be added to your resume library when you save.
          </p>
        )}
      </div>

      {showOutcome && (
        <div className="border-t pt-4">
          <h4 className="font-medium text-gray-900 mb-3">Outcome Details</h4>
          <div className="mb-4">
            <label className="block text-sm font-medium text-gray-700 mb-1">Rejection Reason</label>
            <select name="outcomeReason" value={formData.outcomeReason || ''} onChange={handleChange}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500">
              <option value="">-- Select reason --</option>
              <option value="position_filled">Position Filled</option>
              <option value="not_qualified">Not Qualified</option>
              <option value="culture_fit">Culture Fit</option>
              <option value="salary_mismatch">Salary Mismatch</option>
              <option value="other">Other</option>
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Feedback / Notes</label>
            <textarea name="feedback" value={formData.feedback} onChange={handleChange} rows={4}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              placeholder="Record any feedback received, lessons learned..." />
          </div>
        </div>
      )}

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Job Description</label>
        <textarea name="description" value={formData.description} onChange={handleChange} rows={3}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          placeholder="Paste the job description or key requirements..." />
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Personal Notes</label>
        <textarea name="notes" value={formData.notes} onChange={handleChange} rows={2}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          placeholder="Why you're interested, contacts, etc." />
      </div>

      {job && (
        <div className="border-t pt-4">
          <h4 className="flex items-center gap-2 font-medium text-gray-900 mb-3">
            <History className="w-4 h-4" />
            Status History
          </h4>
          {isLoadingHistory ? (
            <p className="text-sm text-gray-400">Loading history...</p>
          ) : history.length > 0 ? (
            <ol className="space-y-3">
              {history.map((entry) => (
                <li key={entry.id} className="flex items-start gap-3">
                  <span className="mt-1.5 w-2 h-2 rounded-full bg-indigo-400 flex-shrink-0" />
                  <div>
                    <p className="text-sm text-gray-900">
                      {entry.fromStatus
                        ? `${STATUS_LABELS[entry.fromStatus]} to ${STATUS_LABELS[entry.toStatus]}`
                        : `Created as ${STATUS_LABELS[entry.toStatus]}`}
                    </p>
                    <p className="text-xs text-gray-400">{new Date(entry.changedAt).toLocaleString()}</p>
                  </div>
                </li>
              ))}
            </ol>
          ) : (
            <p className="text-sm text-gray-400">No transitions recorded yet.</p>
          )}
        </div>
      )}

      <div className="flex items-center justify-between pt-4 border-t">
        {job && onDelete && (
          <button type="button" onClick={onDelete} disabled={isSubmitting}
            className="flex items-center gap-2 px-4 py-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors disabled:opacity-50">
            <Trash2 className="w-4 h-4" />
            Delete
          </button>
        )}
        <div className={!job ? 'ml-auto' : ''}>
          <button type="submit" disabled={isSubmitting}
            className="flex items-center gap-2 px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
            <Save className="w-4 h-4" />
            {isSubmitting ? 'Saving...' : job ? 'Save Changes' : 'Add Job'}
          </button>
        </div>
      </div>
    </form>
  );
}
