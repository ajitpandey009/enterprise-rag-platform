import { NavLink } from 'react-router-dom';
import { MessageSquare, FileText, LogOut, Brain } from 'lucide-react';

export default function Sidebar({ user, onLogout }) {
  return (
    <aside className="sidebar">
      <div className="sidebar-header">
        <div className="sidebar-logo">
          <div className="logo-icon">
            <Brain size={18} color="white" />
          </div>
          <span>RAG Platform</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        <NavLink to="/chat" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <MessageSquare size={18} />
          AI Chat
        </NavLink>
        <NavLink to="/documents" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
          <FileText size={18} />
          Documents
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <div className="user-info">
          <div className="user-avatar">
            {user?.username?.charAt(0).toUpperCase() || 'U'}
          </div>
          <div className="user-details">
            <div className="user-name">{user?.fullName || user?.username}</div>
            <div className="user-role">{user?.role}</div>
          </div>
          <button className="logout-btn" onClick={onLogout} title="Logout">
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </aside>
  );
}
