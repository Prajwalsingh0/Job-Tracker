import { useEffect, useState } from 'react';
import { useJobs } from '@/context/JobContext';
import { api } from '@/lib/api';
import { AiStatus, AiTaskName, GenerateResult, MatchResult } from '@/types';
import { useToast } from '@/components/ui/Toast';
import {
  Sparkles, AlertCircle, Loader2, Info, CheckCircle2, XCircle, Wand2, Target,
} from 'lucide-react';

const TASKS: { task: AiTaskName; label: string; needsText: boolean }[] = [
  { task: 'JOB_SUMMARY', label: 'Summarise the posting', needsText: false },
  { task: 'INTERVIEW_QUESTIONS', label: 'Likely interview questions', needsText: true },
  { task: 'COVER_LETTER', label: 'Draft a cover letter', needsText: true },
  { task: 'STAR_PRACTICE', label: 'STAR practice prompts', needsText: true },
  { task: 'LEARNING_PLAN', label: 'Learning plan', needsText: true },
];

export function AssistantPage() {
  const { state } = useJobs();
  const { showToast } = useToast();

  const [status, setStatus] = useState<AiStatus | null>(null);
  const [jobId, setJobId] = useState('');
  const [resumeText, setResumeText] = useState('');

  const [result, setResult] = useState<MatchResult | null>(null);
  const [isMatching, setIsMatching] = useState(false);

  const [generated, setGenerated] = useState<GenerateResult | null>(null);
  const [activeTask, setActiveTask] = useState<AiTaskName | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .aiStatus()
      .then(setStatus)
      .catch(() => setStatus({ provider: 'unknown', available: false }));
  }, []);

  useEffect(() => {
    if (!jobId && state.jobs.length > 0) {
      setJobId(String(state.jobs[0].id));
    }
  }, [state.jobs, jobId]);

  const selectedJob = state.jobs.find((job) => String(job.id) === jobId);

  const handleMatch = async () => {
    setError(null);
    setResult(null);
    setIsMatching(true);
    try {
      setResult(await api.aiMatch({ jobId: Number(jobId), resumeText }));
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not analyse the match';
      setError(message);
      showToast(message, 'error');
    } finally {
      setIsMatching(false);
    }
  };

  const handleGenerate = async (task: AiTaskName) => {
    setError(null);
    setGenerated(null);
    setActiveTask(task);
    try {
      setGenerated(await api.aiGenerate({ jobId: Number(jobId), task, resumeText: resumeText || undefined }));
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not generate content';
      setError(message);
      showToast(message, 'error');
    } finally {
      setActiveTask(null);
    }
  };

  const hasJobs = state.jobs.length > 0;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Assistant</h1>
          <p className="text-gray-500 mt-1">
            Compare your experience with a posting, and get help preparing
          </p>
        </div>
        {status && (
          <span
            className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium border ${
              status.available
                ? 'bg-green-50 text-green-700 border-green-200'
                : 'bg-amber-50 text-amber-700 border-amber-200'
            }`}
          >
            <span className={`w-2 h-2 rounded-full ${status.available ? 'bg-green-500' : 'bg-amber-500'}`} />
            {status.available ? `Generated content: ${status.provider}` : 'Generated content: not configured'}
          </span>
        )}
      </div>

      {status && !status.available && status.hint && (
        <div className="flex items-start gap-3 bg-amber-50 text-amber-800 p-4 rounded-lg text-sm">
          <Info className="w-5 h-5 flex-shrink-0 mt-0.5" />
          <span>{status.hint}</span>
        </div>
      )}

      {error && (
        <div className="flex items-start gap-3 bg-red-50 text-red-600 p-4 rounded-lg text-sm">
          <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
          <span>{error}</span>
        </div>
      )}

      {!hasJobs ? (
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm px-6 py-16 text-center">
          <Target className="w-12 h-12 mx-auto mb-3 text-gray-300" />
          <p className="text-gray-600 font-medium">Add a job first</p>
          <p className="text-gray-400 text-sm mt-1">
            The assistant compares your experience against a job description you have saved.
          </p>
        </div>
      ) : (
        <>
          {/* Inputs */}
          <section className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 space-y-5">
            <div>
              <label htmlFor="assistant-job" className="block text-sm font-medium text-gray-700 mb-1">
                Job
              </label>
              <select
                id="assistant-job"
                value={jobId}
                onChange={(event) => setJobId(event.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              >
                {state.jobs.map((job) => (
                  <option key={job.id} value={job.id}>
                    {job.companyName} · {job.jobTitle}
                  </option>
                ))}
              </select>
              {selectedJob && !selectedJob.description && (
                <p className="mt-2 text-xs text-amber-700">
                  This job has no description saved. Matching needs it — add one from All Jobs.
                </p>
              )}
            </div>

            <div>
              <label htmlFor="assistant-resume" className="block text-sm font-medium text-gray-700 mb-1">
                Your experience (paste the text you want compared)
              </label>
              <textarea
                id="assistant-resume"
                rows={8}
                value={resumeText}
                onChange={(event) => setResumeText(event.target.value)}
                placeholder="Paste your resume, or the parts relevant to this role. Only this text is compared — nothing is inferred."
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 font-mono text-sm"
              />
              <p className="mt-1 text-xs text-gray-400">
                {resumeText.length} / 20000 characters. Nothing here is stored.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <button
                onClick={() => void handleMatch()}
                disabled={isMatching || !resumeText.trim() || !selectedJob?.description}
                className="flex items-center gap-2 px-5 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isMatching ? <Loader2 className="w-4 h-4 animate-spin" /> : <Target className="w-4 h-4" />}
                {isMatching ? 'Analysing...' : 'Analyse match'}
              </button>
              <span className="text-xs text-gray-400">Works without an AI provider.</span>
            </div>
          </section>

          {/* Match result */}
          {result && (
            <section className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 space-y-5">
              <h2 className="font-semibold text-gray-900">
                {result.companyName} · {result.jobTitle}
              </h2>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="rounded-xl border border-gray-200 p-4">
                  <p className="text-sm text-gray-500">Skill coverage</p>
                  <p className="text-3xl font-bold text-gray-900">{result.skillCoverage}%</p>
                  <div className="mt-2 bg-gray-200 rounded-full h-2 overflow-hidden">
                    <div className="bg-indigo-500 h-full" style={{ width: `${result.skillCoverage}%` }} />
                  </div>
                </div>
                <div className="rounded-xl border border-gray-200 p-4">
                  <p className="text-sm text-gray-500">Keyword overlap</p>
                  <p className="text-3xl font-bold text-gray-900">{result.keywordOverlap}%</p>
                  <div className="mt-2 bg-gray-200 rounded-full h-2 overflow-hidden">
                    <div className="bg-purple-500 h-full" style={{ width: `${result.keywordOverlap}%` }} />
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-3 bg-gray-50 text-gray-600 p-3 rounded-lg text-xs">
                <Info className="w-4 h-4 flex-shrink-0 mt-0.5" />
                <span>{result.disclaimer}</span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                <div>
                  <h3 className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
                    <CheckCircle2 className="w-4 h-4 text-green-600" />
                    In both ({result.matchedSkills.length})
                  </h3>
                  {result.matchedSkills.length === 0 ? (
                    <p className="text-sm text-gray-400">No recognised skills overlap.</p>
                  ) : (
                    <ul className="flex flex-wrap gap-1.5">
                      {result.matchedSkills.map((skill) => (
                        <li key={skill} className="rounded-full bg-green-50 text-green-700 text-xs px-2 py-0.5">
                          {skill}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                <div>
                  <h3 className="flex items-center gap-2 text-sm font-medium text-gray-700 mb-2">
                    <XCircle className="w-4 h-4 text-amber-600" />
                    In the posting only ({result.missingSkills.length})
                  </h3>
                  {result.missingSkills.length === 0 ? (
                    <p className="text-sm text-gray-400">Nothing missing from your text.</p>
                  ) : (
                    <ul className="flex flex-wrap gap-1.5">
                      {result.missingSkills.map((skill) => (
                        <li key={skill} className="rounded-full bg-amber-50 text-amber-700 text-xs px-2 py-0.5">
                          {skill}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>

              {result.suggestions.length > 0 && (
                <div>
                  <h3 className="text-sm font-medium text-gray-700 mb-2">Suggestions</h3>
                  <ul className="space-y-2 text-sm text-gray-600 list-disc list-inside">
                    {result.suggestions.map((suggestion) => (
                      <li key={suggestion}>{suggestion}</li>
                    ))}
                  </ul>
                </div>
              )}
            </section>
          )}

          {/* Generation */}
          <section className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 space-y-5">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-lg bg-indigo-500">
                <Wand2 className="w-5 h-5 text-white" />
              </div>
              <h2 className="font-semibold text-gray-900">Preparation help</h2>
            </div>

            {!status?.available && (
              <p className="text-sm text-gray-500">
                These use an AI provider. Without one they report that clearly instead of inventing content.
              </p>
            )}

            <div className="flex flex-wrap gap-2">
              {TASKS.map((entry) => {
                const disabled = !status?.available || activeTask !== null || (entry.needsText && !resumeText.trim());
                return (
                  <button
                    key={entry.task}
                    onClick={() => void handleGenerate(entry.task)}
                    disabled={disabled}
                    className="flex items-center gap-2 px-4 py-2 text-sm border border-gray-300 rounded-lg bg-white hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {activeTask === entry.task ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Sparkles className="w-4 h-4 text-indigo-500" />
                    )}
                    {entry.label}
                  </button>
                );
              })}
            </div>

            {generated && (
              <div className="rounded-xl border border-gray-200 p-4 bg-gray-50">
                <p className="text-xs uppercase tracking-wide text-gray-500 mb-2">
                  {generated.task.replace(/_/g, ' ').toLowerCase()} · {generated.provider}
                </p>
                <p className="text-sm text-gray-800 whitespace-pre-line">{generated.content}</p>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
