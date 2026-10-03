import React, { useState, useCallback, useMemo, useEffect } from 'react';
import {
  ReactFlow,
  MiniMap,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
  addEdge,
  Handle,
  Position,
  NodeProps,
  BackgroundVariant,
  Panel,
  MarkerType,
  ReactFlowProvider,
  useReactFlow,
  EdgeLabelRenderer,
  BaseEdge,
  getSmoothStepPath
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import dagre from '@dagrejs/dagre';

import { toPng } from 'html-to-image';
import { 
  GitFork, 
  Download, 
  Sparkles, 
  Loader2,
  Plus, 
  Edit2, 
  Trash2, 
  X, 
  Check, 
  HelpCircle,
  LayoutGrid,
  ArrowRightLeft,
  ZoomIn,
  Layers,
  BookOpen,
  BrainCircuit,
  Lightbulb,
  FileText,
  Search,
  ChevronDown,
  ChevronRight,
  Filter,
  Maximize2,
  Minimize2,
  Star,
  ExternalLink
} from 'lucide-react';
import { useStudy } from '../../context/StudyContext';
import { MindmapNode as StudyMindmapNode, MindmapNodeType, MindmapEdge as StudyMindmapEdge } from '../../types';

// Utility for node type metadata (badge color, icon)
const NODE_TYPE_META: Record<string, { label: string; bg: string; text: string; icon: any }> = {
  topic: { label: 'Chủ đề chính', bg: 'bg-emerald-100 border-emerald-300', text: 'text-emerald-900', icon: Layers },
  subtopic: { label: 'Chủ đề phụ', bg: 'bg-teal-100 border-teal-300', text: 'text-teal-900', icon: Layers },
  concept: { label: 'Khái niệm', bg: 'bg-amber-100 border-amber-300', text: 'text-amber-900', icon: BrainCircuit },
  definition: { label: 'Định nghĩa', bg: 'bg-blue-100 border-blue-300', text: 'text-blue-900', icon: BookOpen },
  process: { label: 'Quy trình', bg: 'bg-indigo-100 border-indigo-300', text: 'text-indigo-900', icon: GitFork },
  method: { label: 'Phương pháp', bg: 'bg-cyan-100 border-cyan-300', text: 'text-cyan-900', icon: LayoutGrid },
  formula: { label: 'Công thức', bg: 'bg-rose-100 border-rose-300', text: 'text-rose-900', icon: Sparkles },
  example: { label: 'Ví dụ', bg: 'bg-yellow-100 border-yellow-300', text: 'text-yellow-900', icon: Lightbulb },
  fact: { label: 'Sự thật', bg: 'bg-slate-100 border-slate-300', text: 'text-slate-900', icon: FileText },
  comparison: { label: 'So sánh', bg: 'bg-purple-100 border-purple-300', text: 'text-purple-900', icon: ArrowRightLeft },
  advantage: { label: 'Ưu điểm', bg: 'bg-emerald-100 border-emerald-300', text: 'text-emerald-800', icon: Check },
  disadvantage: { label: 'Nhược điểm', bg: 'bg-rose-100 border-rose-300', text: 'text-rose-800', icon: X },
  application: { label: 'Ứng dụng', bg: 'bg-sky-100 border-sky-300', text: 'text-sky-900', icon: Check },
  warning: { label: 'Lưu ý', bg: 'bg-orange-100 border-orange-300', text: 'text-orange-900', icon: HelpCircle },
  important: { label: 'Trọng tâm', bg: 'bg-purple-100 border-purple-300', text: 'text-purple-900', icon: Star },
};

// Custom Mindmap Root Node Component (Central Highlight)
function MindmapRootNode({ data }: NodeProps) {
  const onAddChild = data?.onAddChild as (parentId: string) => void;
  const label = (data?.label as string) || 'Nút gốc';
  const summary = (data?.summary || data?.detail) as string || 'Chủ đề kiến thức trọng tâm bài học';

  return (
    <div className="group relative bg-[#0F766E] text-white p-5 px-7 rounded-3xl shadow-2xl border-4 border-teal-300/60 flex items-center gap-4 transition-all hover:scale-105 touch-none select-none min-w-[280px]">
      <div className="w-12 h-12 rounded-2xl bg-white/20 backdrop-blur-md flex items-center justify-center shrink-0 border border-white/30 shadow-inner">
        <GitFork className="w-6 h-6 text-teal-100" />
      </div>
      <div>
        <div className="flex items-center gap-1.5 mb-1">
          <span className="bg-teal-800/80 text-teal-100 text-[10px] font-extrabold px-2.5 py-0.5 rounded-full uppercase tracking-wider border border-teal-400/30">
            Nút gốc trung tâm (Root)
          </span>
        </div>
        <h3 className="font-extrabold text-base md:text-lg tracking-wide uppercase text-white leading-snug">
          {label}
        </h3>
        <p className="text-[11px] text-teal-100/90 font-medium line-clamp-1 mt-0.5">
          {summary}
        </p>
      </div>

      {/* Quick Add Child Button on Root Node */}
      <button
        onClick={(e) => {
          e.stopPropagation();
          if (typeof onAddChild === 'function') onAddChild('root-node');
        }}
        className="nodrag nopan absolute -right-4 top-1/2 -translate-y-1/2 w-8 h-8 bg-[#0F766E] hover:bg-teal-800 text-white rounded-full border-2 border-white shadow-lg flex items-center justify-center opacity-90 group-hover:opacity-100 group-hover:scale-110 transition-all cursor-pointer z-20"
        title="Thêm nhánh mới từ Nút gốc"
      >
        <Plus className="w-4 h-4" />
      </button>

      <Handle type="source" position={Position.Right} id="root-right" className="!bg-[#0F766E] !w-3 !h-3 !border-2 !border-white" />
      <Handle type="source" position={Position.Left} id="root-left" className="!bg-[#0F766E] !w-3 !h-3 !border-2 !border-white" />
    </div>
  );
}

// Custom Mindmap Deep Node Component
function MindmapDeepNode({ id, data, selected }: NodeProps) {
  const onAddChild = data?.onAddChild as (parentId: string) => void;
  const onDeleteNode = data?.onDeleteNode as (nodeId: string) => void;
  const onToggleCollapse = data?.onToggleCollapse as (nodeId: string) => void;
  const onExpandAI = data?.onExpandAI as (node: any) => void;

  const nodeType = (data?.type as string) || 'concept';
  const meta = NODE_TYPE_META[nodeType] || NODE_TYPE_META.concept;

  const importance = (data?.importance as number) || 3;
  const isHighImportance = importance >= 4;
  const isCollapsed = Boolean(data?.isCollapsed);
  const childCount = typeof data?.childCount === 'number'
    ? data.childCount
    : (Array.isArray(data?.children) ? data.children.length : 0);
  const isSearchMatch = Boolean(data?.isSearchMatch);
  const label = (data?.label as string) || 'Nút sơ đồ';
  const summary = (data?.summary || data?.detail) as string || '';

  return (
    <div
      className={`group relative p-4 rounded-2xl border-2 transition-all shadow-md min-w-[260px] max-w-[320px] space-y-2.5 bg-white touch-none select-none ${
        isSearchMatch
          ? 'border-amber-500 ring-4 ring-amber-400/40 shadow-2xl scale-[1.03] bg-amber-50/20'
          : selected
          ? 'border-[#0F766E] ring-4 ring-[#0F766E]/20 shadow-xl scale-[1.02]'
          : 'border-slate-200/90 hover:border-teal-400/70 hover:shadow-lg'
      }`}
    >
      <Handle type="target" position={Position.Left} className="!bg-[#0F766E] !w-3 !h-3 !border-2 !border-white" />

      {/* Node Type & Importance Header */}
      <div className="flex items-center justify-between gap-2 border-b border-slate-100 pb-2">
        <div className="flex items-center gap-1.5 overflow-hidden">
          <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border flex items-center gap-1 ${meta.bg} ${meta.text}`}>
            <span className="truncate">{meta.label}</span>
          </span>

          {/* Importance Rating Stars */}
          {isHighImportance && (
            <span className="bg-amber-100 text-amber-800 text-[10px] font-extrabold px-1.5 py-0.5 rounded-md flex items-center gap-0.5 border border-amber-200">
              <span>{importance}/5</span>
            </span>
          )}
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-1 opacity-90 sm:opacity-0 group-hover:opacity-100 transition-opacity">
          {typeof onExpandAI === 'function' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onExpandAI({ id, label, summary, level: data?.level });
              }}
              className="nodrag nopan px-1.5 py-0.5 hover:bg-teal-50 text-teal-700 text-[10px] font-semibold rounded transition-colors"
              title="Mở rộng đào sâu nhánh này"
            >
              Mở rộng
            </button>
          )}

          {typeof onDeleteNode === 'function' && id !== 'root-node' && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onDeleteNode(id);
              }}
              className="nodrag nopan p-1 hover:bg-rose-50 text-slate-400 hover:text-rose-600 rounded transition-colors"
              title="Xóa nút"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Node Label */}
      <h4 className="font-extrabold text-xs md:text-sm text-[#111827] leading-snug">
        {data.label as string}
      </h4>

      {/* Node Summary / Detail */}
      {(data.summary || data.detail) && (
        <p className="text-[11px] text-slate-600 leading-relaxed font-normal line-clamp-2 bg-slate-50 p-2 rounded-xl border border-slate-100">
          {(data.summary || data.detail) as string}
        </p>
      )}

      {/* Subdetails Badges */}
      {data.subDetails && Array.isArray(data.subDetails) && data.subDetails.length > 0 && (
        <div className="flex flex-wrap gap-1 pt-0.5">
          {(data.subDetails as string[]).map((sub: string, idx: number) => (
            <span key={idx} className="bg-slate-100 text-slate-700 text-[10px] px-2 py-0.5 rounded-md font-medium border border-slate-200">
              {sub}
            </span>
          ))}
        </div>
      )}

      {/* Fold / Unfold Collapse Toggle Button */}
      {childCount > 0 && onToggleCollapse && (
        <button
          onClick={(e) => {
            e.stopPropagation();
            onToggleCollapse(id);
          }}
          className="nodrag nopan absolute -left-3 top-1/2 -translate-y-1/2 w-6 h-6 bg-white hover:bg-slate-100 text-slate-700 rounded-full border border-slate-300 shadow-md flex items-center justify-center transition-all cursor-pointer z-10"
          title={isCollapsed ? `Mở rộng ${childCount} nút con` : `Thu gọn các nút con`}
        >
          {isCollapsed ? <ChevronRight className="w-3.5 h-3.5 text-[#0F766E]" /> : <ChevronDown className="w-3.5 h-3.5 text-slate-500" />}
        </button>
      )}

      {/* Quick Add Child Handle (+ button right on node border) */}
      <button
        onClick={(e) => {
          e.stopPropagation();
          if (onAddChild) onAddChild(id);
        }}
        className="nodrag nopan absolute -right-3.5 top-1/2 -translate-y-1/2 w-7 h-7 bg-[#0F766E] hover:bg-teal-800 text-white rounded-full border-2 border-white shadow-md flex items-center justify-center opacity-90 group-hover:opacity-100 group-hover:scale-110 transition-all cursor-pointer z-10"
        title="Thêm nút con từ nhánh này"
      >
        <Plus className="w-4 h-4" />
      </button>

      <Handle type="source" position={Position.Right} className="!bg-[#0F766E] !w-3 !h-3 !border-2 !border-white" />
    </div>
  );
}

// Custom Cross-Link Edge Component
function CrossLinkEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data }: any) {
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });

  const relationLabel = data?.type || 'related_to';

  return (
    <>
      <BaseEdge id={id} path={edgePath} style={{ stroke: '#D97706', strokeWidth: 2, strokeDasharray: '5,5' }} />
      {relationLabel && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              pointerEvents: 'all',
            }}
            className="nodrag nopan bg-amber-100/90 text-amber-900 border border-amber-300/80 text-[10px] font-bold px-2 py-0.5 rounded-full shadow-2xs"
          >
            {relationLabel}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

// Robust Balanced Left/Right Tree Layout Generator using Dagre
const getBalancedLayoutedElements = (nodes: StudyMindmapNode[], edges: StudyMindmapEdge[]) => {
  if (!nodes || !Array.isArray(nodes) || nodes.length === 0) return { nodes: [], edges: [] };

  const rootNode = nodes.find(n => n && n.id === 'root-node') || nodes[0];
  if (!rootNode || !rootNode.id) return { nodes: [], edges: [] };

  const otherNodes = nodes.filter(n => n && n.id !== rootNode.id);

  if (otherNodes.length === 0) {
    return {
      nodes: [{ ...rootNode, position: { x: 0, y: 0 } }],
      edges: []
    };
  }

  // Level 1 nodes (children of rootNode or nodes with no parent)
  const level1Nodes = otherNodes.filter(n => !n.parentId || n.parentId === rootNode.id);
  const midPoint = Math.ceil(level1Nodes.length / 2);
  const rightLevel1 = level1Nodes.slice(0, midPoint);
  const leftLevel1 = level1Nodes.slice(midPoint);

  const rightIds = new Set<string>(rightLevel1.map(n => n.id));
  const leftIds = new Set<string>(leftLevel1.map(n => n.id));

  // Recursively collect descendant IDs safely
  const collectDescendants = (parentIds: Set<string>) => {
    let changed = true;
    let iterations = 0;
    while (changed && iterations < 20) {
      changed = false;
      iterations++;
      otherNodes.forEach(n => {
        if (n.parentId && parentIds.has(n.parentId) && !parentIds.has(n.id)) {
          parentIds.add(n.id);
          changed = true;
        }
      });
    }
  };

  collectDescendants(rightIds);
  collectDescendants(leftIds);

  const layoutedNodes = nodes.map((n) => {
    if (n.id === rootNode.id) {
      return {
        ...n,
        position: { x: 0, y: 0 },
        targetPosition: Position.Left,
        sourcePosition: Position.Right
      };
    }

    if (rightIds.has(n.id)) {
      const rIndex = Array.from(rightIds).indexOf(n.id);
      return {
        ...n,
        position: { x: (n.level || 1) * 320, y: (rIndex - rightIds.size / 2) * 160 },
        targetPosition: Position.Left,
        sourcePosition: Position.Right
      };
    }

    const lIndex = Array.from(leftIds).indexOf(n.id);
    return {
      ...n,
      position: { x: -(n.level || 1) * 320, y: (lIndex - leftIds.size / 2) * 160 },
      targetPosition: Position.Right,
      sourcePosition: Position.Left
    };
  });

  // Run Dagre layout with try-catch
  try {
    const g = new dagre.graphlib.Graph();
    g.setDefaultEdgeLabel(() => ({}));
    g.setGraph({ rankdir: 'LR', nodesep: 45, ranksep: 140 });

    layoutedNodes.forEach(n => {
      g.setNode(n.id, { width: 300, height: 130 });
    });

    edges.forEach(e => {
      if (g.hasNode(e.source) && g.hasNode(e.target)) {
        g.setEdge(e.source, e.target);
      }
    });

    dagre.layout(g);

    const dagreNodes = layoutedNodes.map(n => {
      const nodeWithPos = g.node(n.id);
      if (nodeWithPos && typeof nodeWithPos.x === 'number') {
        const isLeft = leftIds.has(n.id);
        return {
          ...n,
          position: {
            x: isLeft ? -nodeWithPos.x - 150 : nodeWithPos.x - 150,
            y: nodeWithPos.y - 65
          },
          targetPosition: isLeft ? Position.Right : Position.Left,
          sourcePosition: isLeft ? Position.Left : Position.Right
        };
      }
      return n;
    });

    return { nodes: dagreNodes, edges };
  } catch (err) {
    console.warn("⚠️ Dagre layout fallback active:", err);
    return { nodes: layoutedNodes, edges };
  }
};

// Internal React Flow Canvas Sub-Component with useReactFlow Camera controls
function MindmapFlowCanvas({
  rawNodesList,
  rawEdgesList,
  rootLabel,
  selectedNodeId,
  setSelectedNodeId,
  onQuickAddChild,
  onQuickDeleteNode,
  onExpandNodeAI,
  searchQuery,
  filterType,
  exportRef,
  showToast,
  levelById,
  childCountById,
  visibleLevel,
  docKey
}: any) {
  const reactFlowInstance = useReactFlow();
  const [collapsedNodeIds, setCollapsedNodeIds] = useState<Set<string>>(new Set());

  // Tier view: fold every node at the chosen level so only levels 1..visibleLevel render.
  // Re-applied only when the tier or the document changes, so manual folds and AI expansions are kept.
  useEffect(() => {
    const next = new Set<string>();
    if (visibleLevel !== 'all') {
      rawNodesList.forEach((n: any) => {
        if (levelById.get(n.id) === visibleLevel && childCountById.get(n.id)) next.add(n.id);
      });
    }
    setCollapsedNodeIds(next);
    const timer = setTimeout(() => reactFlowInstance?.fitView({ padding: 0.2, duration: 400 }), 60);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visibleLevel, docKey]);

  // Export Mindmap Image Handler via html-to-image
  useEffect(() => {
    if (exportRef) {
      exportRef.current = async () => {
        const reactFlowEl = document.querySelector('.react-flow') as HTMLElement;
        if (!reactFlowEl) {
          showToast("Không tìm thấy khung sơ đồ tư duy.");
          return;
        }

        try {
          showToast("Đang chuẩn bị xuất ảnh HD...");

          if (reactFlowInstance) {
            reactFlowInstance.fitView({ padding: 0.2, duration: 200 });
            await new Promise((res) => setTimeout(res, 350));
          }

          const dataUrl = await toPng(reactFlowEl, {
            backgroundColor: '#F8FAFC',
            pixelRatio: 2,
            filter: (node: HTMLElement) => {
              if (node?.classList) {
                if (
                  node.classList.contains('react-flow__minimap') ||
                  node.classList.contains('react-flow__controls') ||
                  node.classList.contains('react-flow__panel')
                ) {
                  return false;
                }
              }
              return true;
            },
          });

          const link = document.createElement('a');
          const safeTitle = (rootLabel || 'mindmap').trim().replace(/[^a-zA-Z0-9_\u00C0-\u024F\u1E00-\u1EFF]/g, '_');
          link.download = `Mindmap_${safeTitle}.png`;
          link.href = dataUrl;
          link.click();

          showToast("Xuất sơ đồ tư duy dạng ảnh PNG thành công!");
        } catch (err: any) {
          console.error("Export mindmap image error:", err);
          showToast(`Không thể xuất ảnh: ${err?.message || 'Lỗi xuất tệp'}`);
        }
      };
    }
    return () => {
      if (exportRef) exportRef.current = null;
    };
  }, [reactFlowInstance, rootLabel, showToast, exportRef]);

  const nodeTypes = useMemo(() => ({
    rootNode: MindmapRootNode,
    branchNode: MindmapDeepNode,
  }), []);

  const edgeTypes = useMemo(() => ({
    crossLink: CrossLinkEdge,
  }), []);

  const toggleCollapseNode = useCallback((nodeId: string) => {
    setCollapsedNodeIds(prev => {
      const next = new Set(prev);
      if (next.has(nodeId)) next.delete(nodeId);
      else next.add(nodeId);
      return next;
    });
  }, []);

  // Filter & Collapse Visibility Calculation
  const visibleNodes = useMemo(() => {
    const hiddenIds = new Set<string>();
    const visited = new Set<string>();

    collapsedNodeIds.forEach(cId => {
      const hideDescendants = (parentId: string) => {
        if (visited.has(parentId)) return;
        visited.add(parentId);
        rawNodesList.forEach((n: any) => {
          if (n.parentId === parentId && n.id !== parentId) {
            hiddenIds.add(n.id);
            hideDescendants(n.id);
          }
        });
      };
      hideDescendants(cId);
    });

    return rawNodesList.filter((n: any) => {
      if (n.id !== 'root-node' && hiddenIds.has(n.id)) return false;

      if (filterType !== 'all') {
        if (filterType === 'important') return (n.importance || 3) >= 4 || n.id === 'root-node';
        if (n.type !== filterType && n.id !== 'root-node') return false;
      }
      return true;
    }).map((n: any) => {
      const isSearchMatch = Boolean(
        searchQuery && 
        n.label && 
        n.label.toLowerCase().includes(searchQuery.toLowerCase())
      );
      return {
        ...n,
        level: levelById.get(n.id) ?? n.level,
        childCount: childCountById.get(n.id) || 0,
        isSearchMatch,
        isCollapsed: collapsedNodeIds.has(n.id)
      };
    });
  }, [rawNodesList, collapsedNodeIds, filterType, searchQuery, levelById, childCountById]);

  const visibleEdges = useMemo(() => {
    const visibleIds = new Set(visibleNodes.map((n: any) => n.id));
    return rawEdgesList.filter((e: any) => visibleIds.has(e.source) && visibleIds.has(e.target));
  }, [rawEdgesList, visibleNodes]);

  // Convert to React Flow state
  const flowNodes = useMemo(() => {
    const { nodes: layoutedNodes } = getBalancedLayoutedElements(visibleNodes, visibleEdges);
    return layoutedNodes.map((n: any) => ({
      id: n.id,
      type: n.id === 'root-node' ? 'rootNode' : 'branchNode',
      data: {
        ...n,
        label: n.id === 'root-node' ? rootLabel : n.label,
        onAddChild: onQuickAddChild,
        onDeleteNode: onQuickDeleteNode,
        onToggleCollapse: toggleCollapseNode,
        onExpandAI: onExpandNodeAI
      },
      position: n.position || { x: 0, y: 0 },
      targetPosition: n.targetPosition,
      sourcePosition: n.sourcePosition
    }));
  }, [visibleNodes, visibleEdges, rootLabel, onQuickAddChild, onQuickDeleteNode, toggleCollapseNode, onExpandNodeAI]);

  const flowEdges = useMemo(() => {
    return visibleEdges.map((e: any) => {
      const isCrossLink = e.type && e.type !== 'contains';
      return {
        id: e.id || `edge-${e.source}-${e.target}`,
        source: e.source,
        target: e.target,
        type: isCrossLink ? 'crossLink' : 'smoothstep',
        data: { type: e.type },
        animated: isCrossLink,
        style: isCrossLink 
          ? { stroke: '#D97706', strokeWidth: 2, strokeDasharray: '5,5' }
          : { stroke: '#0F766E', strokeWidth: 2.5 },
        markerEnd: isCrossLink ? undefined : { type: MarkerType.ArrowClosed, color: '#0F766E' }
      };
    });
  }, [visibleEdges]);

  const [nodes, setNodes, onNodesChange] = useNodesState(flowNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(flowEdges);

  useEffect(() => {
    setNodes(flowNodes);
    setEdges(flowEdges);
  }, [flowNodes, flowEdges, setNodes, setEdges]);

  const onConnect = useCallback(
    (params: any) => setEdges((eds) => addEdge({ ...params, type: 'smoothstep', animated: true, style: { stroke: '#0F766E', strokeWidth: 2.5 } }, eds)),
    [setEdges]
  );

  const onNodeClick = (_: any, node: any) => {
    setSelectedNodeId(node.id);
  };

  // Camera Focus Handler for Search
  useEffect(() => {
    if (searchQuery && reactFlowInstance) {
      const matchedNode = flowNodes.find((n: any) => 
        n.data.label && n.data.label.toLowerCase().includes(searchQuery.toLowerCase())
      );
      if (matchedNode && matchedNode.position) {
        try {
          reactFlowInstance.setCenter(matchedNode.position.x + 150, matchedNode.position.y + 60, { zoom: 1.2, duration: 800 });
        } catch (e) {
          console.warn("setCenter exception caught:", e);
        }
      }
    }
  }, [searchQuery, flowNodes, reactFlowInstance]);

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      onNodeClick={onNodeClick}
      nodeTypes={nodeTypes}
      edgeTypes={edgeTypes}
      nodesDraggable={true}
      nodesConnectable={true}
      elementsSelectable={true}
      panOnDrag={true}
      zoomOnPinch={true}
      panOnScroll={false}
      preventScrolling={true}
      onlyRenderVisibleElements
      fitView
      minZoom={0.15}
      maxZoom={2.5}
    >
      <Controls className="!bg-white/95 !border-slate-200 !shadow-md !rounded-xl !left-2 !bottom-2" />
      <MiniMap 
        zoomable 
        pannable 
        nodeColor={(node) => (node.type === 'rootNode' ? '#0F766E' : '#14B8A6')}
        nodeBorderRadius={6}
        maskColor="rgba(241, 245, 249, 0.75)"
        className="!bg-white !border-2 !border-slate-200 !rounded-2xl !shadow-xl !w-28 sm:!w-36 !h-20 sm:!h-24 !bottom-2 !right-2 !z-10 !overflow-hidden [&>svg]:!overflow-hidden [&>svg]:!w-full [&>svg]:!h-full" 
      />
      <Background variant={BackgroundVariant.Dots} gap={20} size={1.2} color="#CBD5E1" />
      
      <Panel position="top-left" className="bg-white/95 backdrop-blur-xs border border-slate-200 p-2 rounded-xl shadow-xs text-[11px] text-slate-600 font-medium flex items-center gap-1.5 max-w-[90vw]">
        <Sparkles className="w-3.5 h-3.5 text-[#0F766E] shrink-0" />
        <span className="truncate">Chạm (+) tạo nhánh • Bấm Mở rộng để phân tích • 1 ngón kéo canvas</span>
      </Panel>
    </ReactFlow>
  );
}

export function MindmapView() {
  const { 
    activeDocData, 
    setActiveTab, 
    showToast, 
    addMindmapNode, 
    updateMindmapNode, 
    deleteMindmapNode,
    expandMindmapNodeAI 
  } = useStudy();

  const mindmapData = activeDocData?.studyPack?.mindmap;

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterType, setFilterType] = useState<string>('all');

  // Modals state
  const [isAddModalOpen, setIsAddModalOpen] = useState<boolean>(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState<boolean>(false);
  const [targetParentId, setTargetParentId] = useState<string>('root-node');

  const [nodeLabel, setNodeLabel] = useState<string>('');
  const [nodeDetail, setNodeDetail] = useState<string>('');
  const [nodeTypeInput, setNodeTypeInput] = useState<MindmapNodeType>('concept');
  const [nodeImportanceInput, setNodeImportanceInput] = useState<number>(3);

  const rootLabel = mindmapData?.rootLabel || mindmapData?.root?.label || activeDocData?.document?.title || 'TY THỂ (Mitochondria)';

  // Process Mindmap Nodes & Edges from Context
  const rawNodesList: StudyMindmapNode[] = useMemo(() => {
    const list = mindmapData?.nodes || [
      {
        id: "node-1",
        parentId: "root-node",
        label: "Màng ngoài (Nhân)",
        shortLabel: "Màng ngoài",
        type: "subtopic",
        summary: "Cho phép khuếch tán chất qua protein porin màng trơn nhẵn.",
        importance: 4,
        level: 1,
        subDetails: ["Protein porin", "Màng trơn nhẵn"]
      },
      {
        id: "node-2",
        parentId: "root-node",
        label: "Màng trong (Gấp nếp)",
        shortLabel: "Màng trong",
        type: "subtopic",
        summary: "Tạo mào Cristae và chứa Phức hợp ATP Synthase tổng hợp năng lượng.",
        importance: 5,
        level: 1,
        subDetails: ["Tạo mào Cristae", "Phức hợp ATP Synthase", "Chuỗi truyền electron"]
      },
      {
        id: "node-3",
        parentId: "node-2",
        label: "Phức hợp ATP Synthase",
        shortLabel: "ATP Synthase",
        type: "formula",
        summary: "Bơm H+ qua màng trong để xoay trục enzyme tạo năng lượng ATP.",
        importance: 5,
        level: 2,
        subDetails: ["Bơm Proton H+", "Tạo năng lượng ATP"]
      },
      {
        id: "node-4",
        parentId: "root-node",
        label: "Chất nền (Matrix)",
        shortLabel: "Chất nền",
        type: "subtopic",
        summary: "Chứa DNA vòng kép tự sao chép và Ribosome 70S nhân sơ.",
        importance: 4,
        level: 1,
        subDetails: ["DNA vòng kép", "Ribosome 70S", "Enzyme Krebs"]
      }
    ];

    const hasRoot = list.some(n => n.id === 'root-node');
    if (hasRoot) return list;

    const rootObj: StudyMindmapNode = {
      id: 'root-node',
      label: rootLabel,
      shortLabel: rootLabel.slice(0, 20),
      type: 'topic',
      summary: `Sơ đồ tư duy bài học ${rootLabel}`,
      importance: 5,
      level: 0
    };

    return [rootObj, ...list];
  }, [mindmapData, rootLabel]);

  const rawEdgesList: StudyMindmapEdge[] = useMemo(() => {
    return mindmapData?.edges || [
      { id: "e-root-1", source: "root-node", target: "node-1", type: "contains" },
      { id: "e-root-2", source: "root-node", target: "node-2", type: "contains" },
      { id: "e-2-3", source: "node-2", target: "node-3", type: "contains" },
      { id: "e-root-4", source: "root-node", target: "node-4", type: "contains" },
      { id: "e-cross-3-4", source: "node-3", target: "node-4", type: "depends_on" }
    ];
  }, [mindmapData]);

  // True tree depth and child counts from parentId (stored "level" values can be stale after edits)
  const { levelById, childCountById, maxLevel } = useMemo(() => {
    const byId = new Map(rawNodesList.map((n) => [n.id, n]));
    const levels = new Map<string, number>();
    const childCounts = new Map<string, number>();
    rawNodesList.forEach((n) => {
      if (n.parentId && n.id !== 'root-node') childCounts.set(n.parentId, (childCounts.get(n.parentId) || 0) + 1);
    });
    const levelOf = (id: string, guard = 0): number => {
      const known = levels.get(id);
      if (known !== undefined) return known;
      const n = byId.get(id);
      let lvl = 1;
      if (!n || id === 'root-node') lvl = 0;
      else if (n.parentId && n.parentId !== id && byId.has(n.parentId) && guard < 20) lvl = levelOf(n.parentId, guard + 1) + 1;
      levels.set(id, lvl);
      return lvl;
    };
    rawNodesList.forEach((n) => levelOf(n.id));
    return { levelById: levels, childCountById: childCounts, maxLevel: Math.max(1, ...Array.from(levels.values())) };
  }, [rawNodesList]);

  // Tier view: which levels are unfolded on open. Deep maps open at level 2 so 100 nodes don't render at once.
  const docKey = activeDocData?.document?.id || 'demo';
  const defaultVisibleLevel = (): number | 'all' => {
    const fromBackend = mindmapData?.metadata?.defaultVisibleLevel;
    if (typeof fromBackend === 'number') return fromBackend >= maxLevel ? 'all' : fromBackend;
    return rawNodesList.length > 40 && maxLevel > 2 ? 2 : 'all';
  };
  const [visibleLevel, setVisibleLevel] = useState<number | 'all'>(defaultVisibleLevel);
  useEffect(() => {
    setVisibleLevel(defaultVisibleLevel());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [docKey]);

  const depthLabel = mindmapData?.metadata?.depthLabel;

  const [selectedNodeId, setSelectedNodeId] = useState<string>('node-2');

  const handleQuickAddChild = useCallback((parentId: string) => {
    setTargetParentId(parentId);
    setNodeLabel('');
    setNodeDetail('');
    setNodeTypeInput('concept');
    setNodeImportanceInput(3);
    setIsAddModalOpen(true);
  }, []);

  const handleQuickDeleteNode = useCallback(async (nodeId: string) => {
    if (nodeId === 'root-node') return;
    if (window.confirm("Xóa nút này khỏi sơ đồ tư duy?")) {
      await deleteMindmapNode(nodeId);
    }
  }, [deleteMindmapNode]);

  // Track expanded node IDs
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    setExpandedNodeIds(new Set());
  }, [activeDocData?.document?.id]);

  const handleExpandNodeAI = useCallback(async (node: any) => {
    if (!node || !node.id) return;

    const hasChildren = rawNodesList.some((n) => n.parentId === node.id);
    if (expandedNodeIds.has(node.id) || hasChildren) {
      showToast(`Đã đào sâu và mở rộng nút "${node.label || 'này'}" rồi!`);
      return;
    }

    setExpandedNodeIds((prev) => new Set(prev).add(node.id));
    await expandMindmapNodeAI(node);
  }, [expandedNodeIds, rawNodesList, expandMindmapNodeAI, showToast]);

  const selectedNodeObj = rawNodesList.find((n) => n.id === selectedNodeId) || {
    id: selectedNodeId,
    label: selectedNodeId === 'root-node' ? rootLabel : 'Nút sơ đồ tư duy',
    type: selectedNodeId === 'root-node' ? 'topic' : 'concept',
    summary: 'Chi tiết phân tích khái niệm bài học từ hệ thống AI Deep Mindmap.',
    importance: selectedNodeId === 'root-node' ? 5 : 3,
    level: selectedNodeId === 'root-node' ? 0 : 2,
    subDetails: ['Deep Mindmap Concept', 'Interactive Node']
  };

  const handleOpenAdd = () => {
    setTargetParentId(selectedNodeId);
    setNodeLabel('');
    setNodeDetail('');
    setNodeTypeInput('concept');
    setNodeImportanceInput(3);
    setIsAddModalOpen(true);
  };

  const handleOpenEdit = () => {
    setNodeLabel(selectedNodeObj.label);
    setNodeDetail(selectedNodeObj.summary || selectedNodeObj.detail || '');
    setNodeTypeInput(selectedNodeObj.type || 'concept');
    setNodeImportanceInput(selectedNodeObj.importance || 3);
    setIsEditModalOpen(true);
  };

  const handleSaveAdd = async () => {
    if (!nodeLabel.trim()) return;
    await addMindmapNode({
      label: nodeLabel,
      detail: nodeDetail,
      summary: nodeDetail,
      type: nodeTypeInput,
      importance: nodeImportanceInput,
      parentId: targetParentId
    });
    setIsAddModalOpen(false);
  };

  const handleSaveEdit = async () => {
    if (!nodeLabel.trim()) return;
    await updateMindmapNode(selectedNodeObj.id, {
      label: nodeLabel,
      detail: nodeDetail,
      summary: nodeDetail,
      type: nodeTypeInput,
      importance: nodeImportanceInput
    });
    setIsEditModalOpen(false);
  };

  const handleDelete = async () => {
    if (selectedNodeId === 'root-node') {
      showToast("Không thể xóa nút gốc trung tâm!");
      return;
    }
    if (window.confirm(`Xóa nút "${selectedNodeObj.label}" khỏi sơ đồ tư duy?`)) {
      await deleteMindmapNode(selectedNodeObj.id);
    }
  };

  const exportRef = React.useRef<(() => Promise<void>) | null>(null);
  const [isExporting, setIsExporting] = useState<boolean>(false);

  const handleExportImage = async () => {
    if (exportRef.current) {
      setIsExporting(true);
      try {
        await exportRef.current();
      } finally {
        setIsExporting(false);
      }
    } else {
      showToast("Sơ đồ tư duy chưa tải xong!");
    }
  };

  return (
    <div className="flex flex-col h-[calc(100vh-4rem)] max-w-[1600px] mx-auto overflow-hidden bg-slate-50">
      {/* Top Title & Search Toolbar Bar */}
      <div className="bg-white border-b border-slate-200 px-4 sm:px-6 py-2 flex flex-col md:flex-row items-start md:items-center justify-between gap-3 shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-[#0F766E] text-white flex items-center justify-center shrink-0 shadow-sm">
            <GitFork className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm sm:text-base font-extrabold text-[#111827] flex items-center gap-2">
              <span>Deep Knowledge Mindmap AI</span>
              <span className="bg-teal-100 text-[#0F766E] text-[10px] font-bold px-2 py-0.5 rounded-full border border-teal-200">
                {rawNodesList.length} Nút phân cấp
              </span>
            </h2>
            <p className="text-[11px] text-slate-500 hidden sm:block">
              {depthLabel ? `Chế độ ${depthLabel} • ` : ''}{maxLevel} tầng kiến thức • AI đào sâu nhánh • Căn đối xứng Trái-Phải
            </p>
          </div>
        </div>

        {/* Live Search & Filter Controls */}
        <div className="flex items-center gap-2 w-full md:w-auto overflow-x-auto pb-1 md:pb-0 scrollbar-none">
          {/* Live Search Input */}
          <div className="relative min-w-[160px] sm:min-w-[200px] shrink-0">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm nút (VD: ATP...)..."
              className="w-full bg-slate-50 border border-slate-200 rounded-lg pl-8 pr-3 py-1.5 text-xs text-[#111827] focus:outline-none focus:border-[#0F766E]"
            />
            {searchQuery && (
              <button onClick={() => setSearchQuery('')} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          {/* Tier (level) selector */}
          {maxLevel > 1 && (
            <div className="flex items-center bg-slate-50 border border-slate-200 rounded-lg p-0.5 shrink-0" title="Số tầng hiển thị">
              {[...Array.from({ length: Math.min(maxLevel - 1, 5) }, (_, i) => i + 1), 'all' as const].map((lvl) => (
                <button
                  key={lvl}
                  onClick={() => setVisibleLevel(lvl)}
                  className={`text-[11px] font-bold px-2 py-1 rounded-md transition-colors ${
                    visibleLevel === lvl ? 'bg-[#0F766E] text-white shadow-xs' : 'text-slate-600 hover:bg-white'
                  }`}
                >
                  {lvl === 'all' ? 'Tất cả' : `Tầng ${lvl}`}
                </button>
              ))}
            </div>
          )}

          {/* Type Filter Select */}
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            className="bg-slate-50 border border-slate-200 text-slate-700 text-xs font-semibold px-2.5 py-1.5 rounded-lg focus:outline-none focus:border-[#0F766E] shrink-0"
          >
            <option value="all">Tất cả loại nút</option>
            <option value="important">Mức quan trọng (4-5)</option>
            <option value="subtopic">Chủ đề con</option>
            <option value="concept">Khái niệm</option>
            <option value="definition">Định nghĩa</option>
            <option value="formula">Công thức</option>
            <option value="process">Quy trình</option>
            <option value="example">Ví dụ</option>
          </select>

          {/* Quick Add Button */}
          <button
            onClick={handleOpenAdd}
            className="bg-[#0F766E] hover:bg-[#0D5C53] text-white text-xs font-bold px-3 py-1.5 rounded-lg flex items-center gap-1 shrink-0 shadow-xs transition-all"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Thêm nhánh</span>
          </button>

          {/* Export Image Button */}
          <button
            onClick={handleExportImage}
            disabled={isExporting}
            className="bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold px-2.5 py-1.5 rounded-lg flex items-center gap-1 shrink-0 shadow-2xs disabled:opacity-50 disabled:cursor-not-allowed transition-all"
          >
            {isExporting ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin text-[#0F766E]" />
            ) : (
              <Download className="w-3.5 h-3.5" />
            )}
            <span className="hidden sm:inline">{isExporting ? 'Đang xuất...' : 'Xuất ảnh'}</span>
          </button>
        </div>
      </div>

      {/* Main Split Layout: React Flow Canvas & Node Inspector Drawer */}
      <div className="flex-1 flex flex-col lg:grid lg:grid-cols-12 overflow-hidden bg-slate-100/70 relative">
        {/* React Flow Interactive Canvas Container */}
        <div className="h-[58vh] sm:h-[62vh] lg:h-full lg:col-span-8 xl:col-span-9 w-full relative border-b lg:border-b-0 lg:border-r border-slate-200 shrink-0 lg:shrink touch-none select-none">
          <ReactFlowProvider>
            <MindmapFlowCanvas
              rawNodesList={rawNodesList}
              rawEdgesList={rawEdgesList}
              rootLabel={rootLabel}
              selectedNodeId={selectedNodeId}
              setSelectedNodeId={setSelectedNodeId}
              onQuickAddChild={handleQuickAddChild}
              onQuickDeleteNode={handleQuickDeleteNode}
              onExpandNodeAI={handleExpandNodeAI}
              searchQuery={searchQuery}
              filterType={filterType}
              exportRef={exportRef}
              showToast={showToast}
              levelById={levelById}
              childCountById={childCountById}
              visibleLevel={visibleLevel}
              docKey={docKey}
            />
          </ReactFlowProvider>
        </div>

        {/* Inspector Panel: Node Details & AI Deep Expansion (Bottom Sheet on Mobile) */}
        <div className="flex-1 lg:h-full lg:col-span-4 xl:col-span-3 bg-white p-4 sm:p-5 space-y-4 overflow-y-auto border-t lg:border-t-0 border-slate-200">
          <div className="space-y-1 border-b border-slate-100 pb-3 flex items-center justify-between">
            <div>
              <span className="text-[10px] font-bold uppercase text-slate-400 tracking-wider">
                Nút đang chọn trên sơ đồ
              </span>
              <h4 className="font-extrabold text-sm sm:text-base text-[#111827] truncate max-w-[200px] sm:max-w-none">
                {selectedNodeObj.label}
              </h4>
            </div>

            <div className="flex items-center gap-1 shrink-0">
              <button
                onClick={handleOpenEdit}
                className="p-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg transition-colors"
                title="Chỉnh sửa nút"
              >
                <Edit2 className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={handleDelete}
                className="p-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-lg transition-colors"
                title="Xóa nút"
              >
                <Trash2 className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          {/* Type Badge & Importance */}
          <div className="flex items-center gap-2">
            <span className="bg-teal-50 text-[#0F766E] border border-teal-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full capitalize">
              Loại: {selectedNodeObj.type || 'Khái niệm'}
            </span>
            <span className="bg-amber-50 text-amber-800 border border-amber-200 text-[11px] font-bold px-2.5 py-0.5 rounded-full flex items-center gap-1">
              <span>Tầm quan trọng: {selectedNodeObj.importance || 3}/5</span>
            </span>
          </div>

          {/* Node Summary & Description Box */}
          <div className="space-y-2">
            <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Mô tả & Giải thích AI:</h5>
            <div className="p-3.5 rounded-xl bg-teal-50/70 border border-teal-100">
              <p className="text-xs text-slate-700 leading-relaxed font-medium">
                {selectedNodeObj.summary || selectedNodeObj.detail || 'Không có mô tả bổ sung cho nút này.'}
              </p>
            </div>
          </div>

          {/* SubDetails Badges */}
          {selectedNodeObj.subDetails && selectedNodeObj.subDetails.length > 0 && (
            <div className="space-y-1">
              <h5 className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Từ khóa & Ý phụ:</h5>
              <div className="flex flex-wrap gap-1.5">
                {selectedNodeObj.subDetails.map((sub: string, i: number) => (
                  <span key={i} className="bg-slate-100 text-slate-700 text-[11px] px-2.5 py-0.5 rounded-md border border-slate-200 font-medium">
                    {sub}
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* AI Deep Expansion & Derived Learning Buttons */}
          <div className="space-y-2 pt-2 border-t border-slate-100">
            <button
              onClick={() => handleExpandNodeAI(selectedNodeObj)}
              className="w-full bg-[#0F766E] hover:bg-[#0D5C53] text-white text-xs font-bold py-2.5 px-3 rounded-xl transition-all shadow-sm flex items-center justify-center"
            >
              <span>Mở rộng & Đào sâu nút này</span>
            </button>

            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => setActiveTab('flashcard')}
                className="w-full bg-[#CCFBF1] hover:bg-teal-200 text-[#0F766E] text-xs font-bold py-2 px-2.5 rounded-xl transition-all flex items-center justify-center gap-1"
              >
                <Layers className="w-3.5 h-3.5" />
                <span>Tạo Flashcard</span>
              </button>
              <button
                onClick={() => setActiveTab('quiz')}
                className="w-full border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold py-2 px-2.5 rounded-xl transition-all flex items-center justify-center gap-1"
              >
                <HelpCircle className="w-3.5 h-3.5 text-[#0F766E]" />
                <span>Tạo Trắc nghiệm</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Add / Edit Mindmap Node Modal */}
      {(isAddModalOpen || isEditModalOpen) && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center z-50 p-4 animate-fadeIn">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-4 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <h3 className="font-bold text-base text-[#111827]">
                {isAddModalOpen ? 'Thêm nút sơ đồ tư duy mới' : 'Chỉnh sửa nút sơ đồ tư duy'}
              </h3>
              <button
                onClick={() => { setIsAddModalOpen(false); setIsEditModalOpen(false); }}
                className="text-slate-400 hover:text-slate-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3">
              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Tên nút nhánh:</label>
                <input
                  type="text"
                  value={nodeLabel}
                  onChange={(e) => setNodeLabel(e.target.value)}
                  placeholder="VD: Enzyme Chu trình Krebs"
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2 text-xs focus:outline-none focus:border-[#0F766E]"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <label className="block text-xs font-bold text-slate-700">Phân loại node:</label>
                  <select
                    value={nodeTypeInput}
                    onChange={(e) => setNodeTypeInput(e.target.value as any)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs text-[#111827]"
                  >
                    <option value="subtopic">Chủ đề con</option>
                    <option value="concept">Khái niệm</option>
                    <option value="definition">Định nghĩa</option>
                    <option value="formula">Công thức</option>
                    <option value="process">Quy trình</option>
                    <option value="example">Ví dụ</option>
                    <option value="application">Ứng dụng</option>
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="block text-xs font-bold text-slate-700">Độ quan trọng (1-5):</label>
                  <select
                    value={nodeImportanceInput}
                    onChange={(e) => setNodeImportanceInput(Number(e.target.value))}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-xs text-[#111827]"
                  >
                    <option value={5}>Mức 5 (Cực kỳ quan trọng)</option>
                    <option value={4}>Mức 4 (Quan trọng)</option>
                    <option value={3}>Mức 3 (Khái niệm chính)</option>
                    <option value={2}>Mức 2 (Kiến thức phụ)</option>
                    <option value={1}>Mức 1 (Ví dụ / Chi tiết)</option>
                  </select>
                </div>
              </div>

              <div className="space-y-1">
                <label className="block text-xs font-bold text-slate-700">Mô tả & Tóm tắt AI:</label>
                <textarea
                  rows={3}
                  value={nodeDetail}
                  onChange={(e) => setNodeDetail(e.target.value)}
                  placeholder="Mô tả chức năng hoặc khái niệm của nút này..."
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs focus:outline-none focus:border-[#0F766E]"
                />
              </div>
            </div>

            <div className="flex gap-2 pt-2 border-t border-slate-100">
              <button
                onClick={isAddModalOpen ? handleSaveAdd : handleSaveEdit}
                className="flex-1 bg-[#0F766E] hover:bg-[#0D5C53] text-white font-bold text-xs py-2.5 rounded-lg shadow-xs flex items-center justify-center gap-1.5"
              >
                <Check className="w-4 h-4" />
                <span>Lưu thay đổi</span>
              </button>
              <button
                onClick={() => { setIsAddModalOpen(false); setIsEditModalOpen(false); }}
                className="flex-1 border border-slate-200 hover:bg-slate-50 text-slate-700 font-bold text-xs py-2.5 rounded-lg"
              >
                Hủy bỏ
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
