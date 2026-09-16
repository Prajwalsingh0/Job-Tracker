import { useEffect, useState } from 'react';
import { useJobs } from '@/context/JobContext';
import { api } from '@/lib/api';
import { JobStats, JobStatusHistoryEntry, STATUS_LABELS } from '@/types';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Briefcase, Send, Users, Trophy, XCircle, TrendingUp, Clock } from 'lucide-react';

const EMPTY_STATS: JobStats = {
  total: 0,
  wishlist: 0,
  applied: 0,
  interviewing: 0,
  offers: 0,
  rejected: 0,
  responseRate: 0,
  interviewRate: 0,
};

export function Dashboard() {
  const { state } = useJobs();
  // Dashboard statistics are computed by the backend (GET /api/jobs/stats).
  const stats = state.stats ?? EMPTY_STATS;

  // A real event log rather than "whatever changed most recently".
  const [activity, setActivity] = useState<JobStatusHistoryEntry[]>([]);
  const [isLoadingActivity, setIsLoadingActivity] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const loadActivity = async () => {
      try {
        const entries = await api.recentActivity();
        if (!cancelled) {
          setActivity(entries);
        }
      } catch {
        // A failed feed must not break the rest of the dashboard.
      } finally {
        if (!cancelled) {
          setIsLoadingActivity(false);
        }
      }
    };

    void loadActivity();
    return () => {
      cancelled = true;
    };
  }, [state.jobs]);

  const statCards = [
    { label: 'Total Jobs', value: stats.total, icon: Briefcase, color: 'bg-slate-500' },
    { label: 'Applied', value: stats.applied, icon: Send, color: 'bg-blue-500' },
    { label: 'Interviewing', value: stats.interviewing, icon: Users, color: 'bg-purple-500' },
    { label: 'Offers', value: stats.offers, icon: Trophy, color: 'bg-green-500' },
    { label: 'Rejected', value: stats.rejected, icon: XCircle, color: 'bg-red-500' },
  ];

  const describeChange = (entry: JobStatusHistoryEntry) =>
    entry.fromStatus
      ? `Moved from ${STATUS_LABELS[entry.fromStatus]} to ${STATUS_LABELS[entry.toStatus]}`
      : `Added as ${STATUS_LABELS[entry.toStatus]}`;

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-500 mt-1">Track your job search progress</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        {statCards.map((stat) => (
          <div key={stat.label} className="bg-white rounded-xl p-5 border border-gray-200 shadow-sm">
            <div className="flex items-center justify-between mb-3">
              <div className={`p-2 rounded-lg ${stat.color}`}>
                <stat.icon className="w-5 h-5 text-white" />
              </div>
            </div>
            <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
            <p className="text-sm text-gray-500">{stat.label}</p>
          </div>
        ))}
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Response Rate */}
        <div className="bg-white rounded-xl p-6 border border-gray-200 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 rounded-lg bg-indigo-500">
              <TrendingUp className="w-5 h-5 text-white" />
            </div>
            <h3 className="font-semibold text-gray-900">Response Rate</h3>
          </div>
          <div className="flex items-end gap-2">
            <span className="text-4xl font-bold text-gray-900">
              {stats.responseRate.toFixed(1)}%
            </span>
            <span className="text-gray-500 mb-1">of applications</span>
          </div>
          <div className="mt-4 bg-gray-200 rounded-full h-2 overflow-hidden">
            <div
              className="bg-indigo-500 h-full rounded-full transition-all duration-500"
              style={{ width: `${Math.min(stats.responseRate, 100)}%` }}
            />
          </div>
        </div>

        {/* Interview Rate */}
        <div className="bg-white rounded-xl p-6 border border-gray-200 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 rounded-lg bg-purple-500">
              <Users className="w-5 h-5 text-white" />
            </div>
            <h3 className="font-semibold text-gray-900">Interview Rate</h3>
          </div>
          <div className="flex items-end gap-2">
            <span className="text-4xl font-bold text-gray-900">
              {stats.interviewRate.toFixed(1)}%
            </span>
            <span className="text-gray-500 mb-1">of applications</span>
          </div>
          <div className="mt-4 bg-gray-200 rounded-full h-2 overflow-hidden">
            <div
              className="bg-purple-500 h-full rounded-full transition-all duration-500"
              style={{ width: `${Math.min(stats.interviewRate, 100)}%` }}
            />
          </div>
        </div>
      </div>

      {/* Recent Activity */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-gray-100">
              <Clock className="w-5 h-5 text-gray-600" />
            </div>
            <h3 className="font-semibold text-gray-900">Recent Activity</h3>
          </div>
        </div>

        {isLoadingActivity ? (
          <div className="px-6 py-12 text-center text-sm text-gray-400">Loading activity...</div>
        ) : activity.length > 0 ? (
          <div className="divide-y divide-gray-100">
            {activity.slice(0, 8).map((entry) => (
              <div key={entry.id} className="px-6 py-4 hover:bg-gray-50 transition-colors">
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white font-semibold flex-shrink-0">
                      {(entry.jobCompanyName ?? '?').charAt(0).toUpperCase()}
                    </div>
                    <div className="min-w-0">
                      <p className="font-medium text-gray-900 truncate">
                        {entry.jobCompanyName ?? 'Job'}
                        {entry.jobTitle && <span className="text-gray-500 font-normal"> · {entry.jobTitle}</span>}
                      </p>
                      <p className="text-sm text-gray-500 truncate">{describeChange(entry)}</p>
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-1 flex-shrink-0">
                    <StatusBadge status={entry.toStatus} />
                    <span className="text-xs text-gray-400">
                      {new Date(entry.changedAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="px-6 py-12 text-center text-gray-500">
            <Briefcase className="w-12 h-12 mx-auto mb-3 text-gray-300" />
            <p>No activity yet</p>
            <p className="text-sm">Add your first job to get started!</p>
          </div>
        )}
      </div>
    </div>
  );
}
