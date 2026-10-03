import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import type { Session, User as SupabaseUser } from '@supabase/supabase-js';
import { supabase } from '../lib/supabase';
import { 
  TabType, 
  User, 
  UserStats, 
  DocumentItem, 
  ActiveDocumentData, 
  OutputOptions,
  MindmapNode,
  Flashcard,
  QuizQuestion,
  LessonHistoryItem,
  KnowledgeFusionResult
} from '../types';

interface StudyContextType {
  activeTab: TabType;
  setActiveTab: (tab: TabType) => void;
  user: User;
  authReady: boolean;
  stats: UserStats | null;
  documents: DocumentItem[];
  activeDocId: string;
  setActiveDocId: (id: string) => void;
  activeDocData: ActiveDocumentData | null;
  fetchDocumentDetail: (docId: string) => Promise<void>;
  loading: boolean;
  toastMessage: string | null;
  showToast: (msg: string) => void;
  uploadDocument: (file: File | null, language: string, depth: string, options: OutputOptions, rawText?: string, fileName?: string) => Promise<any>;
  processVideo: (videoUrl: string) => Promise<void>;
  processUrl: (url: string) => Promise<void>;
  reviewFlashcard: (cardId: string, rating: string) => Promise<void>;
  submitQuiz: (quizId: string, answers: Record<string, number>) => Promise<any>;
  sendChatMessage: (documentId: string, question: string, history: any[]) => Promise<string>;
  
  // Knowledge Fusion Operations
  fusionResult: KnowledgeFusionResult | null;
  fusionLoading: boolean;
  performKnowledgeFusion: (docIds: string[]) => Promise<any>;
  
  // Mindmap Operations
  addMindmapNode: (nodeData: Partial<MindmapNode>) => Promise<void>;
  updateMindmapNode: (nodeId: string, nodeData: Partial<MindmapNode>) => Promise<void>;
  deleteMindmapNode: (nodeId: string) => Promise<void>;
  expandMindmapNodeAI: (node: MindmapNode) => Promise<void>;

  // Flashcard Operations
  addFlashcard: (cardData: Partial<Flashcard>) => Promise<void>;
  deleteFlashcard: (cardId: string) => Promise<void>;
  regenerateFlashcardsAI: (docId?: string) => Promise<void>;

  // Quiz Operations
  addQuizQuestion: (questionData: Partial<QuizQuestion>) => Promise<void>;
  regenerateQuizAI: (docId?: string) => Promise<void>;

  // Prerequisite Analysis ("Kiến thức Tiên quyết")
  analyzePrerequisitesAI: (depth?: string) => Promise<void>;
  submitDiagnostic: (answers: Record<string, number>) => Promise<void>;

  // Lesson History Operations (Supabase Integration)
  historyList: LessonHistoryItem[];
  isSupabaseActive: boolean;
  fetchHistory: () => Promise<void>;
  deleteHistoryItem: (id: string) => Promise<void>;
  continueLessonFromHistory: (id: string) => Promise<void>;

  // Auth & User Personalization
  isAuthenticated: boolean;
  isAuthModalOpen: boolean;
  authMode: 'login' | 'signup';
  isEditProfileOpen: boolean;
  openAuthModal: (mode?: 'login' | 'signup') => void;
  closeAuthModal: () => void;
  openEditProfileModal: () => void;
  closeEditProfileModal: () => void;
  login: (email?: string, password?: string) => Promise<any>;
  signup: (fullName: string, email: string, password?: string) => Promise<any>;
  logout: () => Promise<void>;
  updateUserProfile: (fullName: string, avatarUrl: string) => Promise<any>;
  uploadAvatarFile: (file: File) => Promise<any>;
}

const StudyContext = createContext<StudyContextType | null>(null);

const DEFAULT_AVATAR = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80";
const GUEST_USER: User = { fullName: "Khách ghé thăm", membershipTier: "Basic", avatarUrl: DEFAULT_AVATAR };

const toAppUser = (u: SupabaseUser): User => ({
  id: u.id,
  email: u.email,
  fullName: u.user_metadata?.full_name || u.email?.split('@')[0] || 'Học viên',
  avatarUrl: u.user_metadata?.avatar_url || DEFAULT_AVATAR,
  membershipTier: "Basic",
  createdAt: u.created_at
});

// Supabase Auth messages are English; show the common ones in Vietnamese
const AUTH_ERRORS: Record<string, string> = {
  'Invalid login credentials': 'Email hoặc mật khẩu không đúng.',
  'Email not confirmed': 'Email chưa được xác nhận. Hãy mở email và bấm vào liên kết xác nhận trước khi đăng nhập.',
  'User already registered': 'Email này đã được đăng ký. Hãy chuyển sang Đăng nhập.',
  'Password should be at least 6 characters.': 'Mật khẩu phải có ít nhất 6 ký tự.'
};
const authError = (message: string) => AUTH_ERRORS[message] || message;

export function StudyProvider({ children }: { children: ReactNode }) {
  const [activeTab, setActiveTab] = useState<TabType>('dashboard');
  
  const [user, setUser] = useState<User>(GUEST_USER);
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(false);
  // False until Supabase has restored any saved session, so a signed-in user never flashes the login page
  const [authReady, setAuthReady] = useState<boolean>(false);

  // Supabase owns the session (stored, refreshed and expired by the SDK); the UI just mirrors it
  useEffect(() => {
    const applySession = (session: Session | null) => {
      setIsAuthenticated(Boolean(session));
      setUser(session ? toAppUser(session.user) : GUEST_USER);
      setAuthReady(true);
    };
    supabase.auth.getSession().then(({ data }) => applySession(data.session));
    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => applySession(session));
    return () => sub.subscription.unsubscribe();
  }, []);

  const [isAuthModalOpen, setIsAuthModalOpen] = useState<boolean>(false);
  const [authMode, setAuthMode] = useState<'login' | 'signup'>('login');
  const [isEditProfileOpen, setIsEditProfileOpen] = useState<boolean>(false);

  const openAuthModal = (mode: 'login' | 'signup' = 'login') => {
    setAuthMode(mode);
    setIsAuthModalOpen(true);
    setActiveTab('auth');
  };

  const closeAuthModal = () => {
    setIsAuthModalOpen(false);
    if (activeTab === 'auth') {
      setActiveTab('dashboard');
    }
  };

  const openEditProfileModal = () => setIsEditProfileOpen(true);
  const closeEditProfileModal = () => setIsEditProfileOpen(false);

const getApiUrl = (endpoint: string): string => {
  if (endpoint.startsWith('http://') || endpoint.startsWith('https://')) return endpoint;
  const isNative = typeof window !== 'undefined' && (window as any).Capacitor?.isNativePlatform?.();
  if (isNative) {
    return `https://studymind-app-five.vercel.app${endpoint}`;
  }
  return endpoint;
};

// Every backend call goes through here so it always carries the signed-in user's access token
const apiFetch = async (endpoint: string, init: RequestInit = {}): Promise<Response> => {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  return fetch(getApiUrl(endpoint), {
    ...init,
    headers: { ...(init.headers || {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }
  });
};

const safeFetchJson = async (res: Response): Promise<{ ok: boolean; data: any; errorMsg?: string }> => {
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      const data = await res.json();
      return { ok: res.ok && data.success !== false, data, errorMsg: data.error };
    } catch (e: any) {
      return { ok: false, data: null, errorMsg: "Dữ liệu phản hồi từ máy chủ không đúng định dạng JSON." };
    }
  } else {
    const text = await res.text();
    if (res.status === 413 || text.includes('Request Entity Too Large') || text.includes('Payload Too Large')) {
      return { 
        ok: false, 
        data: null, 
        errorMsg: "Tệp tin quá lớn! Giới hạn trên Vercel tối đa 4.5MB. Vui lòng chọn tệp nhỏ hơn." 
      };
    }
    return { 
      ok: false, 
      data: null, 
      errorMsg: text ? text.slice(0, 120) : `Lỗi máy chủ (Mã lỗi ${res.status})` 
    };
  }
};

  // Session changes (sign in / out) update user state via onAuthStateChange
  const login = async (email?: string, password?: string) => {
    const { error } = await supabase.auth.signInWithPassword({ email: (email || '').trim(), password: password || '' });
    if (error) return { success: false, error: authError(error.message) };
    showToast("Đăng nhập thành công!");
    return { success: true };
  };

  const signup = async (fullName: string, email: string, password?: string) => {
    const cleanEmail = email.trim().toLowerCase();
    const displayName = fullName.trim() || cleanEmail.split('@')[0];
    const { data, error } = await supabase.auth.signUp({
      email: cleanEmail,
      password: password || '',
      options: { data: { full_name: displayName, avatar_url: DEFAULT_AVATAR }, emailRedirectTo: window.location.origin }
    });
    if (error) return { success: false, error: authError(error.message) };
    // With "Confirm email" on, Supabase returns no session until the link in the email is clicked
    if (!data.session) {
      return { success: false, error: `Đã gửi email xác nhận tới ${cleanEmail}. Hãy bấm vào liên kết trong email rồi quay lại đăng nhập.` };
    }
    showToast(`Tạo tài khoản thành công! Chào mừng ${displayName}!`);
    return { success: true };
  };

  const updateUserProfile = async (fullName: string, avatarUrl: string) => {
    const { data, error } = await supabase.auth.updateUser({ data: { full_name: fullName.trim(), avatar_url: avatarUrl.trim() || DEFAULT_AVATAR } });
    if (error) {
      showToast("Lỗi cập nhật hồ sơ!");
      return { success: false, error: error.message };
    }
    setUser(toAppUser(data.user));
    showToast("Đã cập nhật hồ sơ cá nhân thành công!");
    return { success: true, user: toAppUser(data.user) };
  };

  const uploadAvatarFile = async (file: File) => {
    try {
      const formData = new FormData();
      formData.append('avatar', file);
      const res = await apiFetch('/api/v1/user/upload-avatar', { method: 'POST', body: formData });
      const data = await res.json();
      if (!data.success) throw new Error(data.error);
      const { data: updated, error } = await supabase.auth.updateUser({ data: { avatar_url: data.avatarUrl } });
      if (error) throw error;
      setUser(toAppUser(updated.user));
      showToast("Tải ảnh đại diện thành công!");
      return { success: true, avatarUrl: data.avatarUrl, user: toAppUser(updated.user) };
    } catch (err: any) {
      console.error("Upload avatar error:", err);
      showToast("Lỗi tải ảnh đại diện!");
      return { success: false, error: err.message };
    }
  };

  const logout = async () => {
    await supabase.auth.signOut();
    // Nothing from this account may linger for the next person on this device
    localStorage.removeItem('studymind_cached_history');
    setHistoryList([]);
    setStats(null);
    setDocuments([]);
    setActiveDocData(null);
    setActiveTab('dashboard');
    showToast("👋 Đã đăng xuất tài khoản!");
  };

  const [stats, setStats] = useState<UserStats | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [activeDocId, setActiveDocId] = useState<string>('doc-1');
  const [activeDocData, setActiveDocData] = useState<ActiveDocumentData | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const [historyList, setHistoryList] = useState<LessonHistoryItem[]>(() => {
    try {
      const cached = localStorage.getItem('studymind_cached_history');
      return cached ? JSON.parse(cached) : [];
    } catch {
      return [];
    }
  });
  const [isSupabaseActive, setIsSupabaseActive] = useState<boolean>(true);

  const [fusionResult, setFusionResult] = useState<KnowledgeFusionResult | null>(null);
  const [fusionLoading, setFusionLoading] = useState<boolean>(false);

  const performKnowledgeFusion = async (docIds: string[]) => {
    setFusionLoading(true);
    try {
      const res = await apiFetch('/api/v1/fusion/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ documentIds: docIds })
      });
      const data = await res.json();
      if (data.success && data.data) {
        setFusionResult(data.data);
        showToast("✨ Đã hợp nhất & phân tích đối chiếu thành công!");
      } else {
        showToast(data.error || "Không thể thực hiện hợp nhất tài liệu");
      }
      return data;
    } catch (err: any) {
      console.error("Knowledge fusion error:", err);
      showToast("Lỗi khi kết nối hệ thống hợp nhất!");
      return { success: false, error: err.message };
    } finally {
      setFusionLoading(false);
    }
  };

  const fetchDashboardStats = async () => {
    try {
      const res = await apiFetch('/api/v1/user/stats');
      const data = await res.json();
      if (data.success) {
        setStats(data.data);
        setDocuments(data.data.recentDocuments || []);
      }
    } catch (err) {
      console.error("Failed to fetch dashboard stats:", err);
    }
  };

  const fetchHistory = async () => {
    try {
      const res = await apiFetch('/api/v1/history');
      const data = await res.json();
      if (data.success && Array.isArray(data.data)) {
        setHistoryList(data.data);
        setIsSupabaseActive(Boolean(data.isSupabaseActive));
        try {
          localStorage.setItem('studymind_cached_history', JSON.stringify(data.data));
        } catch (e) {}
      }
    } catch (err) {
      console.error("Failed to fetch lesson history:", err);
      try {
        const cached = localStorage.getItem('studymind_cached_history');
        if (cached) {
          setHistoryList(JSON.parse(cached));
        }
      } catch (e) {}
    }
  };

  const deleteHistoryItem = async (id: string) => {
    try {
      const res = await apiFetch(`/api/v1/history/${id}`, { method: 'DELETE' });
      const data = await res.json();
      if (data.success) {
        showToast("Đã xóa bài học khỏi lịch sử!");
        await fetchHistory();
        await fetchDashboardStats();
      }
    } catch (err) {
      console.error("Delete history item error:", err);
    }
  };

  const continueLessonFromHistory = async (id: string) => {
    await fetchDocumentDetail(id);
    setActiveTab('workspace');
  };

  const fetchDocumentDetail = async (docId: string) => {
    try {
      setLoading(true);
      const res = await apiFetch(`/api/v1/documents/${docId}`);
      const data = await res.json();
      if (data.success) {
        setActiveDocData(data);
        setActiveDocId(docId);
      }
    } catch (err) {
      console.error(`Failed to fetch doc ${docId}:`, err);
    } finally {
      setLoading(false);
    }
  };

  // Load the signed-in user's data once their session is known (and again after switching accounts)
  useEffect(() => {
    if (!user.id) return;
    fetchDashboardStats();
    fetchHistory();
  }, [user.id]);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 4000);
  };

  const uploadDocument = async (file: File | null, language: string, depth: string, options: OutputOptions, rawText?: string, fileName?: string) => {
    try {
      const formData = new FormData();
      if (file) formData.append('file', file);
      if (rawText) formData.append('rawText', rawText);
      if (fileName) formData.append('fileName', fileName);
      formData.append('language', language);
      formData.append('depth', depth);
      formData.append('options', JSON.stringify(options));

      const res = await apiFetch('/api/v1/documents/upload', {
        method: 'POST',
        body: formData
      });
      
      const parsed = await safeFetchJson(res);

      if (parsed.ok && parsed.data) {
        showToast("Đã chuyển hóa tài liệu thành công!");
        await fetchDashboardStats();
        await fetchHistory();
        setActiveDocData({ document: parsed.data.document, studyPack: parsed.data.studyPack });
        setActiveDocId(parsed.data.document.id);
        setActiveTab('workspace');
        return parsed.data;
      } else {
        showToast(parsed.errorMsg || "Lỗi tải tài liệu!");
        return { success: false, error: parsed.errorMsg };
      }
    } catch (err: any) {
      console.error("Upload document failed:", err);
      showToast(err?.message || "Lỗi tải tài liệu!");
    }
  };

  const processVideo = async (videoUrl: string) => {
    try {
      const res = await apiFetch('/api/v1/documents/process-video', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ videoUrl })
      });
      const parsed = await safeFetchJson(res);
      if (parsed.ok && parsed.data) {
        showToast("📹 Đã trích xuất transcript từ Video!");
        await fetchDashboardStats();
        await fetchHistory();
        setActiveDocData({ document: parsed.data.document, studyPack: parsed.data.studyPack });
        setActiveDocId(parsed.data.document.id);
        setActiveTab('workspace');
      } else {
        showToast(parsed.errorMsg || "Lỗi xử lý Video!");
      }
    } catch (err: any) {
      console.error("Process video failed:", err);
      showToast(err?.message || "Lỗi xử lý Video!");
    }
  };

  const processUrl = async (url: string) => {
    try {
      const res = await apiFetch('/api/v1/documents/process-url', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url })
      });
      const parsed = await safeFetchJson(res);
      if (parsed.ok && parsed.data) {
        showToast("🔗 Đã trích xuất bài viết Web thành công!");
        await fetchDashboardStats();
        await fetchHistory();
        setActiveDocData({ document: parsed.data.document, studyPack: parsed.data.studyPack });
        setActiveDocId(parsed.data.document.id);
        setActiveTab('workspace');
      } else {
        showToast(parsed.errorMsg || "Lỗi xử lý URL bài viết!");
      }
    } catch (err: any) {
      console.error("Process URL failed:", err);
      showToast(err?.message || "Lỗi xử lý URL bài viết!");
    }
  };

  // Mindmap Operations
  const addMindmapNode = async (nodeData: Partial<MindmapNode>) => {
    try {
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/mindmap/nodes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nodeData)
      });
      const data = await res.json();
      if (data.success) {
        showToast(`🌿 Đã thêm nút mới vào sơ đồ tư duy: "${nodeData.label}"`);
        await fetchDocumentDetail(activeDocId);
      }
    } catch (err) {
      console.error("Add mindmap node failed:", err);
    }
  };

  const updateMindmapNode = async (nodeId: string, nodeData: Partial<MindmapNode>) => {
    try {
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/mindmap/nodes/${nodeId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(nodeData)
      });
      const data = await res.json();
      if (data.success) {
        showToast(`Đã cập nhật nút sơ đồ tư duy!`);
        await fetchDocumentDetail(activeDocId);
      }
    } catch (err) {
      console.error("Update mindmap node failed:", err);
    }
  };

  const deleteMindmapNode = async (nodeId: string) => {
    try {
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/mindmap/nodes/${nodeId}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (data.success) {
        showToast(`Đã xóa nút sơ đồ tư duy!`);
        await fetchDocumentDetail(activeDocId);
      }
    } catch (err) {
      console.error("Delete mindmap node failed:", err);
    }
  };

  // Flashcard Operations
  const addFlashcard = async (cardData: Partial<Flashcard>) => {
    try {
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/flashcards`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cardData)
      });
      const data = await res.json();
      if (data.success) {
        showToast(`Đã tạo thẻ ghi nhớ mới thành công!`);
        await fetchDocumentDetail(activeDocId);
      }
    } catch (err) {
      console.error("Add flashcard failed:", err);
    }
  };

  const reviewFlashcard = async (cardId: string, rating: string) => {
    try {
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/flashcards/${cardId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ rating })
      });
      const data = await res.json();
      if (data.success) {
        showToast(data.message);
        // Keep the saved rating in state (no refetch per card); the Knowledge Gap Map reads it
        setActiveDocData(prev => prev && {
          ...prev,
          studyPack: { ...prev.studyPack, flashcards: (prev.studyPack.flashcards || []).map(c => c.id === cardId ? data.card : c) }
        });
      }
    } catch (err) {
      console.error("Review flashcard failed:", err);
    }
  };

  const deleteFlashcard = async (cardId: string) => {
    try {
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/flashcards/${cardId}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (data.success) {
        showToast(`Đã xóa thẻ ghi nhớ!`);
        await fetchDocumentDetail(activeDocId);
      }
    } catch (err) {
      console.error("Delete flashcard failed:", err);
    }
  };

  // Quiz Operations
  const addQuizQuestion = async (questionData: Partial<QuizQuestion>) => {
    try {
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/quiz/questions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(questionData)
      });
      const data = await res.json();
      if (data.success) {
        showToast(`❓ Đã thêm câu hỏi trắc nghiệm mới vào bộ kiểm tra!`);
        await fetchDocumentDetail(activeDocId);
      }
    } catch (err) {
      console.error("Add quiz question failed:", err);
    }
  };

  const submitQuiz = async (quizId: string, answers: Record<string, number>) => {
    try {
      const res = await apiFetch(`/api/v1/quiz/${quizId}/submit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ answers })
      });
      const data = await res.json();
      if (data.success) {
        fetchDashboardStats();
        fetchHistory();
        fetchDocumentDetail(quizId); // fresh quizHistory for the Knowledge Gap Map
      }
      return data;
    } catch (err) {
      console.error("Submit quiz failed:", err);
    }
  };

  const sendChatMessage = async (documentId: string, question: string, history: any[]) => {
    try {
      const res = await apiFetch('/api/ai/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ documentId, question, chatHistory: history })
      });
      const data = await res.json();
      return data.answer;
    } catch (err) {
      console.error("Send chat message failed:", err);
      return "Rất tiếc, máy chủ AI đang bận. Bạn vui lòng thử lại sau giây lát.";
    }
  };

  const expandMindmapNodeAI = async (node: MindmapNode) => {
    try {
      showToast(`Đang mở rộng và phân tích sâu nút "${node.label}"...`);
      const res = await apiFetch(`/api/v1/documents/${activeDocId}/mindmap/expand`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ node })
      });
      const data = await res.json();
      if (data.success) {
        showToast(`Đã đào sâu và tạo các nhánh con cho "${node.label}"!`);
        await fetchDocumentDetail(activeDocId);
      }
    } catch (err) {
      console.error("Expand mindmap node failed:", err);
      showToast("Không thể mở rộng nút. Vui lòng thử lại.");
    }
  };

  const regenerateQuizAI = async (docId?: string) => {
    const targetId = docId || activeDocId;
    try {
      showToast("Đang biên soạn 10-12 câu hỏi trắc nghiệm mới...");
      const res = await apiFetch(`/api/v1/documents/${targetId}/regenerate-quiz`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
      });
      const data = await res.json();
      if (data.success && data.quiz) {
        showToast("Đã sinh mới 10-12 câu hỏi trắc nghiệm thành công!");
        if (activeDocData) {
          setActiveDocData({
            ...activeDocData,
            studyPack: {
              ...activeDocData.studyPack,
              quiz: data.quiz
            }
          });
        }
      }
    } catch (err) {
      console.error("Regenerate quiz failed:", err);
      showToast("Không thể tạo câu hỏi trắc nghiệm lúc này!");
    }
  };

  const analyzePrerequisitesAI = async (depth?: string) => {
    const doc = activeDocData?.document;
    if (!doc) return;
    try {
      const res = await apiFetch(`/api/v1/documents/${doc.id}/prerequisites`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ depth })
      });
      const parsed = await safeFetchJson(res);
      if (parsed.ok && parsed.data?.prerequisites) {
        setActiveDocData(prev => prev && prev.document.id === doc.id
          ? { ...prev, studyPack: { ...prev.studyPack, prerequisites: parsed.data.prerequisites } }
          : prev);
        showToast(parsed.data.prerequisites.isFallback
          ? "AI đang bận, đã tạo phân tích sơ bộ. Hãy thử lại sau ít phút."
          : "Đã phân tích xong kiến thức tiên quyết!");
      } else {
        showToast(parsed.errorMsg || "Không thể phân tích kiến thức tiên quyết lúc này!");
      }
    } catch (err) {
      console.error("Prerequisite analysis failed:", err);
      showToast("Không thể phân tích kiến thức tiên quyết lúc này!");
    }
  };

  // Graded and stored server-side as evidence for the Knowledge Gap Map
  const submitDiagnostic = async (answers: Record<string, number>) => {
    const doc = activeDocData?.document;
    if (!doc) return;
    try {
      const res = await apiFetch(`/api/v1/documents/${doc.id}/prerequisites/diagnostic`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ answers })
      });
      const parsed = await safeFetchJson(res);
      if (parsed.ok && parsed.data?.prerequisites) {
        setActiveDocData(prev => prev && prev.document.id === doc.id
          ? { ...prev, studyPack: { ...prev.studyPack, prerequisites: parsed.data.prerequisites } }
          : prev);
      } else {
        showToast(parsed.errorMsg || "Không lưu được kết quả bài test chẩn đoán.");
      }
    } catch (err) {
      console.error("Submit diagnostic failed:", err);
      showToast("Không lưu được kết quả bài test chẩn đoán.");
    }
  };

  const regenerateFlashcardsAI = async (docId?: string) => {
    const targetId = docId || activeDocId;
    try {
      showToast("Đang khởi tạo 10-12 Thẻ ghi nhớ mới...");
      const res = await apiFetch(`/api/v1/documents/${targetId}/regenerate-flashcards`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({})
      });
      const data = await res.json();
      if (data.success && data.flashcards) {
        showToast("Đã sinh mới 10-12 Thẻ ghi nhớ thành công!");
        if (activeDocData) {
          setActiveDocData({
            ...activeDocData,
            studyPack: {
              ...activeDocData.studyPack,
              flashcards: data.flashcards
            }
          });
        }
      }
    } catch (err) {
      console.error("Regenerate flashcards failed:", err);
      showToast("Không thể tạo thẻ ghi nhớ lúc này!");
    }
  };

  return (
    <StudyContext.Provider value={{
      activeTab,
      setActiveTab,
      user,
      authReady,
      stats,
      documents,
      activeDocId,
      setActiveDocId,
      activeDocData,
      fetchDocumentDetail,
      loading,
      toastMessage,
      showToast,
      uploadDocument,
      processVideo,
      processUrl,
      reviewFlashcard,
      submitQuiz,
      sendChatMessage,
      addMindmapNode,
      updateMindmapNode,
      deleteMindmapNode,
      expandMindmapNodeAI,
      addFlashcard,
      deleteFlashcard,
      regenerateFlashcardsAI,
      addQuizQuestion,
      regenerateQuizAI,
      analyzePrerequisitesAI,
      submitDiagnostic,
      historyList,
      isSupabaseActive,
      fetchHistory,
      deleteHistoryItem,
      continueLessonFromHistory,
      isAuthenticated,
      isAuthModalOpen,
      authMode,
      isEditProfileOpen,
      openAuthModal,
      closeAuthModal,
      openEditProfileModal,
      closeEditProfileModal,
      login,
      signup,
      logout,
      updateUserProfile,
      uploadAvatarFile,
      fusionResult,
      fusionLoading,
      performKnowledgeFusion
    }}>
      {children}
    </StudyContext.Provider>
  );
}

export function useStudy() {
  const context = useContext(StudyContext);
  if (!context) throw new Error("useStudy must be used within StudyProvider");
  return context;
}
