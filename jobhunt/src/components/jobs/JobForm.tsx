import { useState, useEffect } from 'react';
import { Job, JobStatus, Resume, STATUS_LABELS, OutcomeReason } from '@/types';
import { useJobs } from '@/context/JobContext';
import { Building2, Briefcase, Link, MapPin, DollarSign, FileText, Calendar, Save, Trash2, Upload } from 'lucide-react';

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
    status: initialStatus,
    appliedDate: new Date().toISOString().split('T')[0],
    targetApplyDate: '',
    outcome: null as Job['outcome'],
    outcomeReason: null as OutcomeReason,
    feedback: '',
    notes: '',
    resumeId: '',
  });

  const [resumeFile, setResumeFile] = useState<File | null>(null);

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
        outcome: job.outcome || null,
        outcomeReason: job.outcomeReason || null,
        feedback: job.feedback || '',
        notes: job.notes || '',
        resumeId: job.resumeId || '',
      });
    }
  }, [job]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleResumeUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
    if (!validTypes.includes(file.type)) {
      alert('Please upload a PDF or DOCX file');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      alert('File size must be less than 10MB');
      return;
    }

    setResumeFile(file);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const resumeId = formData.resumeId;

    if (resumeFile) {
      const reader = new FileReader();
      const fileData = await new Promise<string>((resolve) => {
        reader.onload = () => resolve(reader.result as string);
        reader.readAsDataURL(resumeFile);
      });

      const newResume: Omit<Resume, 'id' | 'createdAt'> = {
        name: resumeFile.name.replace(/\.[^/.]+$/, ''),
        fileName: resumeFile.name,
        fileData,
        fileType: resumeFile.type.includes('pdf') ? 'pdf' : 'docx',
        versionTag: `${formData.companyName} - ${formData.jobTitle}`,
      };

      addResume(newResume);
    }

    const jobData = {
      companyName: formData.companyName,
      jobTitle: formData.jobTitle,
      jobUrl: formData.jobUrl || undefined,
      description: formData.description || undefined,
      location: formData.location || undefined,
      salaryRange: formData.salaryRange || undefined,
      status: formData.status,
      appliedDate: formData.status !== 'wishlist' ? formData.appliedDate : undefined,
      targetApplyDate: formData.targetApplyDate || undefined,
      outcome: formData.outcome,
      outcomeReason: formData.outcomeReason,
      feedback: formData.feedback || undefined,
      notes: formData.notes || undefined,
      resumeId: resumeId || undefined,
    };

    if (job) {
      updateJob({ ...job, ...jobData });
    } else {
      addJob(jobData);
    }

    onSave();
  };

  const showAppliedDate = formData.status !== 'wishlist';
  const showOutcome = ['offer', 'rejected', 'withdrawn', 'ghosted'].includes(formData.status);

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
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
        {resumeFile && <p className="mt-2 text-sm text-green-600">Selected: {resumeFile.name}</p>}
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

      <div className="flex items-center justify-between pt-4 border-t">
        {job && onDelete && (
          <button type="button" onClick={onDelete}
            className="flex items-center gap-2 px-4 py-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors">
            <Trash2 className="w-4 h-4" />
            Delete
          </button>
        )}
        <div className={!job ? 'ml-auto' : ''}>
          <button type="submit"
            className="flex items-center gap-2 px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors">
            <Save className="w-4 h-4" />
            {job ? 'Save Changes' : 'Add Job'}
          </button>
        </div>
      </div>
    </form>
  );
}
