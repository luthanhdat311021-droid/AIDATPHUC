// Source of the "studymind-api" Supabase Edge Function: the same Express routes as backend/server.js
// (backend/routes/studyRoutes.js), so the native app needs no Vercel deploy. Files are uploaded to Supabase
// Storage and lessons are built in short steps (/api/v1/lessons/start → analyze → finish).
// Build: node supabase/functions/studymind-api/build.mjs → index.ts (single file to deploy).
import express from 'express';
import { registerStudyRoutes, jsonErrorHandler } from '../../../../backend/routes/studyRoutes.js';

const app = express();

// Requests arrive as /studymind-api/api/...; the shared routes expect /api/...
app.use((req, res, next) => {
  req.url = req.url.replace(/^\/studymind-api(?=\/|$)/, '') || '/';
  next();
});

app.use((req, res, next) => {
  res.set({
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
  });
  if (req.method === 'OPTIONS') return res.status(204).end();
  next();
});

// Only JSON reaches this function; files go to Storage and are fetched by path
app.use(express.json({ limit: '10mb' }));

registerStudyRoutes(app);

app.use((req, res) => res.status(404).json({ success: false, error: `Không có API ${req.method} ${req.path}` }));
app.use(jsonErrorHandler);

app.listen(8000);
