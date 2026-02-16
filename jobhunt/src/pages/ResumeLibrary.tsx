import { useState } from 'react';
import { useJobs } from '@/context/JobContext';
import { Resume } from '@/types';
import { FileText, Upload, Trash2, Download, Tag, Calendar, Eye } from 'lucide-react';

export function ResumeLibrary() {
  const { state, addResume, deleteResume } = useJobs();
  const [isUploading, setIsUploading] = useState(false);

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
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

    setIsUploading(true);

    const reader = new FileReader();
    const fileData = await new Promise<string>((resolve) => {
      reader.onload = () => resolve(reader.result as string);
      reader.readAsDataURL(file);
    });

    const versionTag = prompt('Enter a version tag (e.g., "Technical v2", "Marketing Focus"):', file.name.replace(/\.[^/.]+$/, ''));

    addResume({
      name: file.name.replace(/\.[^/.]+$/, ''),
      fileName: file.name,
      fileData,
      fileType: file.type.includes('pdf') ? 'pdf' : 'docx',
      versionTag: versionTag || undefined,
    });

    setIsUploading(false);
    e.target.value = '';
  };

  const handleDelete = (resume: Resume) => {
    if (window.confirm(`Delete "${resume.name}"? This cannot be undone.`)) {
      deleteResume(resume.id);
    }
  };

  const handleDownload = (resume: Resume) => {
    const link = document.createElement('a');
    link.href = resume.fileData;
    link.download = resume.fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handlePreview = (resume: Resume) => {
    if (resume.fileType === 'pdf') {
      const byteString = atob(resume.fileData.split(',')[1]);
      const mimeString = resume.fileData.split(',')[0].split(':')[1].split(';')[0];
      const ab = new ArrayBuffer(byteString.length);
      const ia = new Uint8Array(ab);
      for (let i = 0; i < byteString.length; i++) {
        ia[i] = byteString.charCodeAt(i);
      }
      const blob = new Blob([ab], { type: mimeString });
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
    } else {
      handleDownload(resume);
    }
  };

  // Count resume usage
  const getUsageCount = (resumeId: string) => {
    return state.jobs.filter(job => job.resumeId === resumeId).length;
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

      {/* Resumes Grid */}
      {state.resumes.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {state.resumes.map((resume) => {
            const usageCount = getUsageCount(resume.id);
            return (
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
                    Used in {usageCount} {usageCount === 1 ? 'application' : 'applications'}
                  </span>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 pt-3 border-t border-gray-100">
                  <button
                    onClick={() => handlePreview(resume)}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
                  >
                    <Eye className="w-4 h-4" />
                    Preview
                  </button>
                  <button
                    onClick={() => handleDownload(resume)}
                    className="flex-1 flex items-center justify-center gap-1.5 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors"
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
            );
          })}
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
