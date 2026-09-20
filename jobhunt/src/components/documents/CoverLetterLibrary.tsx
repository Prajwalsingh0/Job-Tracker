import { useCallback, useEffect, useState } from 'react';
import { useJobs } from '@/context/JobContext';
import { api, fetchCoverLetterFile } from '@/lib/api';
import { CoverLetter } from '@/types';
import { ConfirmDialog } from '@/components/ui/ConfirmDialog';
import { SkeletonCards } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';
import {
  Mail, Plus, Trash2, Download, FileText, Calendar, Link2, X, Loader2, Save,
} from 'lucide-react';

const MAX_FILE_BYTES = 10 * 1024 * 1024;
const ALLOWED_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
];

/**
 * Cover letters: written as pasted text or uploaded as a PDF/DOCX, optionally linked to one
 * of your tracked jobs. Documents are stored by the backend, never in the browser.
 */
export function CoverLetterLibrary() {
  const { state } = useJobs();
  const { showToast } = useToast();

  const [letters, setLetters] = useState<CoverLetter[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [pendingDelete, setPendingDelete] = useState<CoverLetter | null>(null);

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [mode, setMode] = useState<'text' | 'file'>('text');
  const [body, setBody] = useState('');
  const [name, setName] = useState('');
  const [versionTag, setVersionTag] = useState('');
  const [jobId, setJobId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const loadLetters = useCallback(async () => {
    setIsLoading(true);
    try {
      setLetters(await api.listCoverLetters());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load cover letters');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadLetters();
  }, [loadLetters]);

  const resetForm = () => {
    setMode('text');
    setBody('');
    setName('');
    setVersionTag('');
    setJobId('');
    setFile(null);
    setFormError(null);
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const selected = event.target.files?.[0];
    if (!selected) return;

    if (!ALLOWED_TYPES.includes(selected.type)) {
      setFormError('Please upload a PDF or DOCX file');
      event.target.value = '';
      return;
    }
    if (selected.size > MAX_FILE_BYTES) {
      setFormError('File size must be less than 10MB');
      event.target.value = '';
      return;
    }

    setFormError(null);
    setFile(selected);
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setFormError(null);

    if (mode === 'text' && !body.trim()) {
      setFormError('Write some text, or switch to uploading a document.');
      return;
    }
    if (mode === 'file' && !file) {
      setFormError('Choose a PDF or DOCX file, or switch to writing text.');
      return;
    }

    setIsSaving(true);
    try {
      await api.createCoverLetter({
        file: mode === 'file' && file ? file : undefined,
        body: mode === 'text' ? body.trim() : undefined,
        name: name.trim() || undefined,
        versionTag: versionTag.trim() || undefined,
        jobId: jobId ? Number(jobId) : undefined,
      });
      resetForm();
      setIsFormOpen(false);
      showToast('Cover letter saved', 'success');
      await loadLetters();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Could not save the cover letter');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDownload = async (letter: CoverLetter) => {
    setBusyId(letter.id);
    try {
      const { blob, fileName } = await fetchCoverLetterFile(letter);
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to download the cover letter');
    } finally {
      setBusyId(null);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) return;
    const letter = pendingDelete;
    setPendingDelete(null);
    try {
      await api.deleteCoverLetter(letter.id);
      showToast(`Deleted ${letter.name}`, 'success');
      await loadLetters();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to delete the cover letter';
      setError(message);
      showToast(message, 'error');
    }
  };

  const jobLabel = (id: number) => {
    const job = state.jobs.find((candidate) => candidate.id === id);
    return job ? `${job.companyName} · ${job.jobTitle}` : `Job #${id}`;
  };

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Cover Letters</h2>
          <p className="text-gray-500 mt-1">
            Write or upload a letter, optionally linked to one of your applications
          </p>
        </div>
        <button
          onClick={() => {
            setIsFormOpen((open) => !open);
            setFormError(null);
          }}
          className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
        >
          {isFormOpen ? <X className="w-5 h-5" /> : <Plus className="w-5 h-5" />}
          {isFormOpen ? 'Cancel' : 'Add Cover Letter'}
        </button>
      </div>

      {error && <div className="bg-red-50 text-red-600 p-3 rounded-lg text-sm">{error}</div>}

      {isFormOpen && (
        <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-gray-200 shadow-sm p-5 space-y-4">
          {formError && <div className="bg-red-50 text-red-600 p-3 rounded-lg text-sm">{formError}</div>}

          {/* Mode switch */}
          <div className="flex gap-2" role="tablist" aria-label="Cover letter source">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'text'}
              onClick={() => setMode('text')}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                mode === 'text' ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              Paste text
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'file'}
              onClick={() => setMode('file')}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                mode === 'file' ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-50'
              }`}
            >
              Upload document
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="cl-name">
                Name
              </label>
              <input
                id="cl-name"
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Backend roles"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="cl-version">
                Version tag
              </label>
              <input
                id="cl-version"
                type="text"
                value={versionTag}
                onChange={(e) => setVersionTag(e.target.value)}
                placeholder="e.g. Draft 2"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="cl-job">
                Linked job
              </label>
              <select
                id="cl-job"
                value={jobId}
                onChange={(e) => setJobId(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              >
                <option value="">Not linked</option>
                {state.jobs.map((job) => (
                  <option key={job.id} value={job.id}>
                    {job.companyName} · {job.jobTitle}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {mode === 'text' ? (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="cl-body">
                Letter
              </label>
              <textarea
                id="cl-body"
                rows={8}
                value={body}
                onChange={(e) => setBody(e.target.value)}
                placeholder="Dear hiring manager..."
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 font-mono text-sm"
              />
              <p className="mt-1 text-xs text-gray-400">{body.length} / 20000 characters</p>
            </div>
          ) : (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1" htmlFor="cl-file">
                Document
              </label>
              <input
                id="cl-file"
                type="file"
                accept=".pdf,.docx"
                onChange={handleFileChange}
                className="block w-full text-sm text-gray-600 file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0 file:bg-gray-100 file:text-gray-700 hover:file:bg-gray-200"
              />
              {file && <p className="mt-2 text-sm text-green-600">Selected: {file.name}</p>}
            </div>
          )}

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={isSaving}
              className="flex items-center gap-2 px-6 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              {isSaving ? 'Saving...' : 'Save Cover Letter'}
            </button>
          </div>
        </form>
      )}

      {isLoading ? (
        <SkeletonCards count={3} />
      ) : letters.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {letters.map((letter) => (
            <div
              key={letter.id}
              className="bg-white rounded-xl border border-gray-200 shadow-sm p-5 hover:shadow-md transition-shadow flex flex-col"
            >
              <div className="flex items-start gap-3 mb-3">
                <div className="p-3 rounded-lg bg-indigo-50">
                  <Mail className="w-6 h-6 text-indigo-600" />
                </div>
                <div className="min-w-0">
                  <h3 className="font-semibold text-gray-900 line-clamp-1">{letter.name}</h3>
                  <span className="text-xs text-gray-500 uppercase">
                    {letter.fileType ?? 'text'}
                  </span>
                </div>
              </div>

              {letter.versionTag && (
                <p className="text-sm text-gray-600 mb-2">Version: {letter.versionTag}</p>
              )}

              {letter.jobId && (
                <p className="flex items-center gap-1.5 text-sm text-indigo-600 mb-2">
                  <Link2 className="w-4 h-4 flex-shrink-0" />
                  <span className="truncate">{jobLabel(letter.jobId)}</span>
                </p>
              )}

              {letter.body && (
                <p className="text-sm text-gray-500 line-clamp-3 mb-3 whitespace-pre-line">{letter.body}</p>
              )}

              <div className="flex items-center gap-2 text-xs text-gray-400 mb-3 mt-auto">
                <Calendar className="w-3.5 h-3.5" />
                {new Date(letter.createdAt).toLocaleDateString()}
                {letter.fileName && (
                  <span className="flex items-center gap-1 ml-2">
                    <FileText className="w-3.5 h-3.5" />
                    <span className="truncate">{letter.fileName}</span>
                  </span>
                )}
              </div>

              <div className="flex items-center gap-2 pt-3 border-t border-gray-100">
                {letter.fileName && (
                  <button
                    onClick={() => handleDownload(letter)}
                    disabled={busyId === letter.id}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors disabled:opacity-50"
                  >
                    <Download className="w-4 h-4" />
                    Download
                  </button>
                )}
                <button
                  onClick={() => setPendingDelete(letter)}
                  aria-label={`Delete ${letter.name}`}
                  className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors ml-auto"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm px-6 py-12 text-center">
          <div className="w-16 h-16 mx-auto mb-4 bg-gray-100 rounded-full flex items-center justify-center">
            <Mail className="w-8 h-8 text-gray-400" />
          </div>
          <p className="text-gray-600 font-medium">No cover letters yet</p>
          <p className="text-gray-400 text-sm mt-1">
            Paste text or upload a PDF/DOCX to keep your letters with the rest of your applications.
          </p>
        </div>
      )}

      <ConfirmDialog
        isOpen={pendingDelete !== null}
        title="Delete this cover letter?"
        message={
          pendingDelete
            ? `"${pendingDelete.name}"${pendingDelete.fileName ? ' and its stored document' : ''} will be removed. This cannot be undone.`
            : ''
        }
        confirmLabel="Delete"
        destructive
        onConfirm={() => void confirmDelete()}
        onCancel={() => setPendingDelete(null)}
      />
    </section>
  );
}
