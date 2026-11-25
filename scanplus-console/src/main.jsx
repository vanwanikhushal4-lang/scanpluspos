import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import SessionBoundary from './SessionBoundary.jsx';
import './styles.css';
import './dashboard-theme.css';
createRoot(document.getElementById('root')).render(<SessionBoundary>{session => <App session={session}/>}</SessionBoundary>);
