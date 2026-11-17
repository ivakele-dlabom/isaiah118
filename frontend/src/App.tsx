import './App.css'
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Main from "./components/main/Main.tsx";
import { BookNavigationProvider } from "./contexts/BookContext.tsx";
import { AuthProvider } from "./contexts/AuthContext.tsx";
import LoginPage from "./pages/LoginPage.tsx";
import SignupPage from "./pages/SignupPage.tsx";
import {VerseSelectionProvider} from "@/contexts/VerseSelectionContext.tsx";
import {ModalProvider} from "@/contexts/ModalContext.tsx";
import {SidebarProvider} from "@/components/ui/sidebar.tsx";
import {Toaster} from "@/components/ui/toast.tsx";


function ProtectedRoute({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}

function AppRoutes() {
    const homePage = (

        <ProtectedRoute>
            <SidebarProvider>
                <ModalProvider>
                    <BookNavigationProvider>
                        <Main />

                        <Toaster />
                    </BookNavigationProvider>
                </ModalProvider>
            </SidebarProvider>
        </ProtectedRoute>
    );
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route
        path="/*"
        element={<Navigate to="/kjv/1/1/1" replace />} 
      />
        <Route path={"/kjv/:bookNumber/:chapterNumber/:verseNumber"} element={
            homePage
        }/>
    </Routes>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
          <VerseSelectionProvider>
              <AppRoutes />
          </VerseSelectionProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App
