import React from 'react';
import { 
  LayoutDashboard, 
  UploadCloud, 
  BookOpen, 
  GitFork, 
  Layers, 
  HelpCircle,
  Compass
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { TabType } from '../../types';

export function MobileBottomBar() {
  const { activeTab, setActiveTab } = useStudy();

  const tabs: Array<{ id: TabType; label: string; icon: any }> = [
    { id: 'dashboard', label: 'Tổng quan', icon: LayoutDashboard },
    { id: 'import', label: 'Nhập file', icon: UploadCloud },
    { id: 'workspace', label: 'Bài học', icon: BookOpen },
    // History stays reachable on mobile via "Xem tất cả" on the dashboard
    { id: 'prerequisite', label: 'Tiên quyết', icon: Compass },
    { id: 'mindmap', label: 'Mindmap', icon: GitFork },
    { id: 'quiz', label: 'Quiz', icon: HelpCircle },
  ];

  return (
    <div className="md:hidden fixed bottom-0 left-0 right-0 bg-white/95 backdrop-blur-md border-t border-slate-200 px-1 py-1.5 flex items-center justify-around z-50 shadow-lg">
      {tabs.map((tab) => {
        const Icon = tab.icon;
        const isActive = activeTab === tab.id;

        return (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex flex-col items-center gap-0.5 transition-all py-1 px-1 rounded-lg min-w-0 ${
              isActive ? 'text-[#0F766E]' : 'text-slate-400 hover:text-slate-600'
            }`}
          >
            <Icon className={`w-4 h-4 shrink-0 ${isActive ? 'scale-110 text-[#0F766E]' : ''}`} />
            <span className={`text-[10px] leading-none truncate max-w-[58px] ${isActive ? 'font-extrabold text-[#0F766E]' : 'font-medium'}`}>
              {tab.label}
            </span>
          </button>
        );
      })}
    </div>
  );
}
