import React from 'react';
import { StudyProvider, useStudy } from './context/StudyContext';
import { Header } from './components/layout/Header';
import { Sidebar } from './components/layout/Sidebar';
import { MobileBottomBar } from './components/layout/MobileBottomBar';
import { DashboardView } from './components/dashboard/DashboardView';
import { ImportView } from './components/import/ImportView';
import { WorkspaceView } from './components/workspace/WorkspaceView';
import { MindmapView } from './components/mindmap/MindmapView';
import { PrerequisiteView } from './components/prerequisite/PrerequisiteView';
import { FlashcardView } from './components/flashcard/FlashcardView';
import { QuizView } from './components/quiz/QuizView';
import { HistoryView } from './components/history/HistoryView';
import { KnowledgeFusionView } from './components/fusion/KnowledgeFusionView';
import { AuthView } from './components/auth/AuthView';
import { EditProfileModal } from './components/auth/EditProfileModal';
import { Toast } from './components/common/Toast';

class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; error: any }
> {
  constructor(props: any) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: any) {
    return { hasError: true, error };
  }

  componentDidCatch(error: any, errorInfo: any) {
    console.error("Uncaught error caught by ErrorBoundary:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="p-8 max-w-xl mx-auto my-12 bg-white rounded-2xl border border-rose-200 shadow-xl space-y-4 text-center">
          <div className="w-12 h-12 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center mx-auto text-xl font-bold">
            !
          </div>
          <h2 className="text-lg font-bold text-slate-900">Đã xảy ra lỗi giao diện</h2>
          <p className="text-xs text-rose-600 bg-rose-50 p-3 rounded-lg font-mono text-left overflow-x-auto">
            {this.state.error?.toString() || 'Lỗi không xác định.'}
          </p>
          <button
            onClick={() => { this.setState({ hasError: false, error: null }); window.location.reload(); }}
            className="bg-[#0F766E] text-white text-xs font-bold px-5 py-2.5 rounded-xl shadow-md"
          >
            Tải lại trang web
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

function MainLayout() {
  const { activeTab, isAuthModalOpen } = useStudy();

  // Standalone Auth Page View
  if (activeTab === 'auth' || isAuthModalOpen) {
    return (
      <div className="min-h-screen bg-slate-100">
        <AuthView />
        <Toast />
      </div>
    );
  }

  return (
    <div className="flex h-screen overflow-hidden bg-[#F8FAFC]">
      {/* Desktop Sidebar */}
      <Sidebar />

      {/* Main Right Content Area */}
      <div className="flex-1 flex flex-col h-screen overflow-y-auto">
        <Header />
        
        <main className="flex-1">
          <ErrorBoundary>
            {activeTab === 'dashboard' && <DashboardView />}
            {activeTab === 'import' && <ImportView />}
            {activeTab === 'history' && <HistoryView />}
            {activeTab === 'fusion' && <KnowledgeFusionView />}
            {activeTab === 'workspace' && <WorkspaceView />}
            {activeTab === 'prerequisite' && <PrerequisiteView />}
            {activeTab === 'mindmap' && <MindmapView />}
            {activeTab === 'flashcard' && <FlashcardView />}
            {activeTab === 'quiz' && <QuizView />}
          </ErrorBoundary>
        </main>
      </div>

      {/* Mobile Navigation Bar */}
      <MobileBottomBar />

      {/* Profile Edit Modal */}
      <EditProfileModal />

      {/* Toast Notification */}
      <Toast />
    </div>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <StudyProvider>
        <MainLayout />
      </StudyProvider>
    </ErrorBoundary>
  );
}
