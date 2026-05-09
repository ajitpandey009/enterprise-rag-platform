import { useState, useEffect, useCallback } from 'react';
import { documentsApi } from '../services/api';
import { Upload, FileText, File, Trash2, CheckCircle, AlertCircle, Loader } from 'lucide-react';
import { useDropzone } from 'react-dropzone';

export default function DocumentsPage({ user }) {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);

  useEffect(() => { loadDocuments(); }, []);

  // Polling for status updates on processing documents
  useEffect(() => {
    const hasProcessing = documents.some(d => d.status === 'PROCESSING' || d.status === 'UPLOADING');
    if (!hasProcessing) return;
    const interval = setInterval(loadDocuments, 3000);
    return () => clearInterval(interval);
  }, [documents]);

  const loadDocuments = async () => {
    try {
      const res = await documentsApi.list();
      setDocuments(res.data.documents || []);
    } catch (err) { console.error('Failed to load documents:', err); }
  };

  const onDrop = useCallback(async (acceptedFiles) => {
    setUploading(true);
    for (const file of acceptedFiles) {
      try {
        await documentsApi.upload(file);
      } catch (err) {
        console.error('Upload failed:', err);
      }
    }
    setUploading(false);
    loadDocuments();
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'application/pdf': ['.pdf'], 'text/plain': ['.txt'] },
    maxSize: 50 * 1024 * 1024
  });

  const handleDelete = async (id) => {
    if (!confirm('Delete this document and all its chunks?')) return;
    try {
      await documentsApi.delete(id);
      loadDocuments();
    } catch (err) { console.error('Delete failed:', err); }
  };

  const formatFileSize = (bytes) => {
    if (!bytes) return '0 B';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + sizes[i];
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'READY': return <CheckCircle size={14} />;
      case 'PROCESSING': case 'UPLOADING': return <Loader size={14} />;
      case 'FAILED': return <AlertCircle size={14} />;
      default: return null;
    }
  };

  return (
    <>
      <div className="page-header">
        <h1>Documents</h1>
        <p>Upload and manage your enterprise knowledge base</p>
      </div>

      <div className="documents-content">
        {/* Upload Zone */}
        <div
          {...getRootProps()}
          className={`upload-zone ${isDragActive ? 'active' : ''}`}
          id="upload-zone"
        >
          <input {...getInputProps()} id="file-input" />
          <div className="upload-zone-icon">
            <Upload size={24} />
          </div>
          {uploading ? (
            <>
              <h3>Uploading...</h3>
              <p>Processing your documents</p>
            </>
          ) : isDragActive ? (
            <>
              <h3>Drop files here</h3>
              <p>Release to upload</p>
            </>
          ) : (
            <>
              <h3>Drag & drop files here</h3>
              <p>or click to browse — Supports PDF and TXT (max 50MB)</p>
            </>
          )}
        </div>

        {/* Document Grid */}
        <div className="documents-grid">
          {documents.map(doc => (
            <div key={doc.id} className="document-card">
              <div className="doc-header">
                <div className={`doc-icon ${doc.contentType?.includes('pdf') ? 'pdf' : 'txt'}`}>
                  {doc.contentType?.includes('pdf') ? <FileText size={20} /> : <File size={20} />}
                </div>
                <div className={`doc-status ${doc.status?.toLowerCase()}`}>
                  {getStatusIcon(doc.status)}
                  {doc.status}
                </div>
              </div>

              <div className="doc-name">{doc.originalFilename}</div>

              <div className="doc-meta">
                <span>{formatFileSize(doc.fileSize)}</span>
                <span>•</span>
                <span>{doc.chunkCount || 0} chunks</span>
                <span>•</span>
                <span>{new Date(doc.uploadedAt).toLocaleDateString()}</span>
              </div>

              {doc.errorMessage && (
                <div style={{ fontSize: 12, color: 'var(--error)', marginBottom: 8 }}>
                  {doc.errorMessage}
                </div>
              )}

              <div className="doc-actions">
                <button
                  className="doc-action-btn delete"
                  onClick={() => handleDelete(doc.id)}
                >
                  <Trash2 size={12} /> Delete
                </button>
              </div>
            </div>
          ))}
        </div>

        {documents.length === 0 && !uploading && (
          <div style={{ textAlign: 'center', padding: 60, color: 'var(--text-muted)' }}>
            <p>No documents uploaded yet. Upload your first document to get started.</p>
          </div>
        )}
      </div>
    </>
  );
}
