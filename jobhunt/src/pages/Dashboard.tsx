import { useJobs } from '@/context/JobContext';
import { Briefcase, Send, Users, Trophy, XCircle, TrendingUp, Clock } from 'lucide-react';

export function Dashboard() {
  const { state, getStats } = useJobs();
  const stats = getStats();

  const recentJobs = [...state.jobs]
    .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
    .slice(0, 5);

  const statCards = [
    { label: 'Total Jobs', value: stats.total, icon: Briefcase, color: 'bg-slate-500' },
    { label: 'Applied', value: stats.applied, icon: Send, color: 'bg-blue-500' },
    { label: 'Interviewing', value: stats.interviewing, icon: Users, color: 'bg-purple-500' },
    { label: 'Offers', value: stats.offers, icon: Trophy, color: 'bg-green-500' },
    { label: 'Rejected', value: stats.rejected, icon: XCircle, color: 'bg-red-500' },
  ];

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
        <div className="divide-y divide-gray-100">
          {recentJobs.length > 0 ? (
            recentJobs.map((job) => (
              <div key={job.id} className="px-6 py-4 hover:bg-gray-50 transition-colors">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center text-white font-semibold">
                      {job.companyName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{job.companyName}</p>
                      <p className="text-sm text-gray-500">{job.jobTitle}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium
                      ${job.status === 'offer' ? 'bg-green-100 text-green-700' :
                        job.status === 'rejected' ? 'bg-red-100 text-red-700' :
                        job.status === 'interview' ? 'bg-purple-100 text-purple-700' :
                        job.status === 'applied' ? 'bg-blue-100 text-blue-700' :
                        'bg-gray-100 text-gray-700'}`}
                    >
                      {job.status.replace('_', ' ')}
                    </span>
                    <p className="text-xs text-gray-400 mt-1">
                      {new Date(job.updatedAt).toLocaleDateString()}
                    </p>
                  </div>
                </div>
              </div>
            ))
          ) : (
            <div className="px-6 py-12 text-center text-gray-500">
              <Briefcase className="w-12 h-12 mx-auto mb-3 text-gray-300" />
              <p>No jobs tracked yet</p>
              <p className="text-sm">Add your first job to get started!</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
