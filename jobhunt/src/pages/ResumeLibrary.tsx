import { useState } from 'react';
import { useJobs } from '@/context/JobContext';
import { fetchResumeFile } from '@/lib/api';
import { Resume } from '@/types';
import { FileText, Upload, Trash2, Download, Tag, Calendar, Eye } from 'lucide-react';

export function ResumeLibrary() {
  const { state, addResume, deleteResume } = useJobs();
  const [isUploading, setIsUploading] = useState(false);
  const [busyResumeId, setBusyResumeId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
    if (!validTypes.includes(file.type)) {
      setError('Please upload a PDF or DOCX file');
      e.target.value = '';
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      setError('File size must be less than 10MB');
      e.target.value = '';
      return;
    }

    const defaultName = file.name.replace(/\.[^/.]+$/, '');
    const versionTag = window.prompt('Enter a version tag (e.g., "Technical v2", "Marketing Focus"):', defaultName);

    setError(null);
    setIsUploading(true);
    try {
      await addResume(file, { versionTag: versionTag || undefined });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setIsUploading(false);
      e.target.value = '';
    }
  };

  const handleDelete = async (resume: Resume) => {
    if (!window.confirm(`Delete "${resume.name}"? This cannot be undone.`)) return;
    try {
      await deleteResume(resume.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete the resume');
    }
  };

  const handleDownload = async (resume: Resume) => {
    setBusyResumeId(resume.id);
    try {
      const { blob, fileName } = await fetchResumeFile(resume);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to download the resume');
    } finally {
      setBusyResumeId(null);
    }
  };

  const handlePreview = async (resume: Resume) => {
    // DOCX previews are not rendered in the browser, so fall back to a download.
    if (resume.fileType !== 'pdf') {
      await handleDownload(resume);
      return;
    }

    setBusyResumeId(resume.id);
    try {
      const { blob } = await fetchResumeFile(resume);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to open the resume');
    } finally {
      setBusyResumeId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Resume Library</h1>
          <p className="text-gray-500 mt-1">Manage your resume versions</p>
        </div>
        <label className={`flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors shadow-sm cursor-pointer ${isUploading ? 'opacity-50 pointer-events-none' : ''}`}>
          <Upload className="w-5 h-5" />
          {isUploading ? 'Uploading...' : 'Upload Resume'}
          <input
            type="file"
            accept=".pdf,.docx"
            onChange={handleUpload}
            className="hidden"
            disabled={isUploading}
          />
        </label>
      </div>

      {error && (
        <div className="bg-red-50 text-red-600 p-3 rounded-lg text-sm">{error}</div>
      )}

      {/* Resumes Grid */}
      {state.resumes.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {state.resumes.map((resume) => (
            <div
              key={resume.id}
              className="bg-white rounded-xl border border-gray-200 shadow-sm p-5 hover:shadow-md transition-shadow"
            >
              {/* Header */}
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className={`p-3 rounded-lg ${resume.fileType === 'pdf' ? 'bg-red-100' : 'bg-blue-100'}`}>
                    <FileText className={`w-6 h-6 ${resume.fileType === 'pdf' ? 'text-red-600' : 'text-blue-600'}`} />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900 line-clamp-1">{resume.name}</h3>
                    <span className="text-xs text-gray-500 uppercase">{resume.fileType}</span>
                  </div>
                </div>
              </div>

              {/* Version Tag */}
              {resume.versionTag && (
                <div className="flex items-center gap-1.5 text-sm text-gray-600 mb-3">
                  <Tag className="w-4 h-4" />
                  <span>{resume.versionTag}</span>
                </div>
              )}

              {/* Meta */}
              <div className="flex items-center gap-4 text-sm text-gray-500 mb-4">
                <span className="flex items-center gap-1">
                  <Calendar className="w-4 h-4" />
                  {new Date(resume.createdAt).toLocaleDateString()}
                </span>
                <span className="text-indigo-600">
                  Used in {resume.usageCount} {resume.usageCount === 1 ? 'application' : 'applications'}
                </span>
              </div>

              {/* Actions */}
              <div className="flex items-center gap-2 pt-3 border-t border-gray-100">
                <button
                  onClick={() => handlePreview(resume)}
                  disabled={busyResumeId === resume.id}
                  className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50"
                >
                  <Eye className="w-4 h-4" />
                  Preview
                </button>
                <button
                  onClick={() => handleDownload(resume)}
                  disabled={busyResumeId === resume.id}
                  className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50"
                >
                  <Download className="w-4 h-4" />
                  Download
                </button>
                <button
                  onClick={() => handleDelete(resume)}
                  className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm px-6 py-16 text-center">
          <div className="w-16 h-16 mx-auto mb-4 bg-gray-100 rounded-full flex items-center justify-center">
            <FileText className="w-8 h-8 text-gray-400" />
          </div>
          <p className="text-gray-600 font-medium">No resumes uploaded yet</p>
          <p className="text-gray-400 text-sm mt-1">Upload your first resume to get started!</p>
        </div>
      )}
    </div>
  );
}
