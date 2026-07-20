import { useState, useEffect, createContext, useContext } from "react";
import "@/App.css";
import { BrowserRouter, Routes, Route, Navigate, Link, useNavigate } from "react-router-dom";
import axios from "axios";
import { Toaster, toast } from "sonner";
import { 
  Key, 
  Shield, 
  Users, 
  Clock, 
  AlertTriangle, 
  CheckCircle, 
  XCircle,
  LogOut,
  Menu,
  X,
  Plus,
  RefreshCw,
  Pause,
  Play,
  Trash2,
  Copy,
  Search,
  BarChart3,
  History,
  Settings
} from "lucide-react";

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL;
const API = `${BACKEND_URL}/api`;

// Auth Context
const AuthContext = createContext(null);

const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
};

const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(localStorage.getItem("token"));
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token) {
      axios.defaults.headers.common["Authorization"] = `Bearer ${token}`;
      fetchUser();
    } else {
      setLoading(false);
    }
  }, [token]);

  const fetchUser = async () => {
    try {
      const response = await axios.get(`${API}/auth/me`);
      setUser(response.data);
    } catch (error) {
      logout();
    } finally {
      setLoading(false);
    }
  };

  const login = async (username, password) => {
    const response = await axios.post(`${API}/auth/login`, { username, password });
    const { token: newToken, ...userData } = response.data;
    localStorage.setItem("token", newToken);
    axios.defaults.headers.common["Authorization"] = `Bearer ${newToken}`;
    setToken(newToken);
    setUser(userData);
    return response.data;
  };

  const register = async (username, email, password) => {
    const response = await axios.post(`${API}/auth/register`, { username, email, password });
    const { token: newToken, ...userData } = response.data;
    localStorage.setItem("token", newToken);
    axios.defaults.headers.common["Authorization"] = `Bearer ${newToken}`;
    setToken(newToken);
    setUser(userData);
    return response.data;
  };

  const logout = () => {
    localStorage.removeItem("token");
    delete axios.defaults.headers.common["Authorization"];
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
};

// Protected Route Component
const ProtectedRoute = ({ children }) => {
  const { token, loading } = useAuth();
  
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-950">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-emerald-500"></div>
      </div>
    );
  }
  
  if (!token) {
    return <Navigate to="/login" />;
  }
  
  return children;
};

// Login Page
const LoginPage = () => {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await login(username, password);
      toast.success("Login successful!");
      navigate("/dashboard");
    } catch (error) {
      toast.error(error.response?.data?.error || "Login failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 px-4">
      <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiMyMjIiIGZpbGwtb3BhY2l0eT0iMC4wNSI+PGNpcmNsZSBjeD0iMzAiIGN5PSIzMCIgcj0iMiIvPjwvZz48L2c+PC9zdmc+')] opacity-40"></div>
      <div className="relative w-full max-w-md">
        <div className="absolute -inset-1 bg-gradient-to-r from-emerald-600 to-cyan-600 rounded-2xl blur opacity-25"></div>
        <div className="relative bg-slate-900/90 backdrop-blur-xl border border-slate-800 rounded-2xl p-8 shadow-2xl">
          <div className="flex items-center justify-center mb-8">
            <div className="p-3 bg-gradient-to-br from-emerald-500 to-cyan-500 rounded-xl">
              <Key className="h-8 w-8 text-white" />
            </div>
          </div>
          <h1 className="text-2xl font-bold text-center text-white mb-2">Welcome Back</h1>
          <p className="text-slate-400 text-center mb-8">Sign in to manage your licenses</p>
          
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Username</label>
              <input
                data-testid="login-username-input"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
                placeholder="Enter your username"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Password</label>
              <input
                data-testid="login-password-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
                placeholder="Enter your password"
                required
              />
            </div>
            <button
              data-testid="login-submit-btn"
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-gradient-to-r from-emerald-600 to-cyan-600 hover:from-emerald-500 hover:to-cyan-500 text-white font-semibold rounded-xl transition-all duration-200 transform hover:scale-[1.02] disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
            >
              {loading ? "Signing in..." : "Sign In"}
            </button>
          </form>
          
          <p className="mt-6 text-center text-slate-400">
            Don't have an account?{" "}
            <Link to="/register" className="text-emerald-400 hover:text-emerald-300 font-medium transition-colors">
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
};

// Register Page
const RegisterPage = () => {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const { register } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await register(username, email, password);
      toast.success("Registration successful!");
      navigate("/dashboard");
    } catch (error) {
      toast.error(error.response?.data?.error || "Registration failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950 px-4">
      <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiMyMjIiIGZpbGwtb3BhY2l0eT0iMC4wNSI+PGNpcmNsZSBjeD0iMzAiIGN5PSIzMCIgcj0iMiIvPjwvZz48L2c+PC9zdmc+')] opacity-40"></div>
      <div className="relative w-full max-w-md">
        <div className="absolute -inset-1 bg-gradient-to-r from-emerald-600 to-cyan-600 rounded-2xl blur opacity-25"></div>
        <div className="relative bg-slate-900/90 backdrop-blur-xl border border-slate-800 rounded-2xl p-8 shadow-2xl">
          <div className="flex items-center justify-center mb-8">
            <div className="p-3 bg-gradient-to-br from-emerald-500 to-cyan-500 rounded-xl">
              <Shield className="h-8 w-8 text-white" />
            </div>
          </div>
          <h1 className="text-2xl font-bold text-center text-white mb-2">Create Account</h1>
          <p className="text-slate-400 text-center mb-8">Start managing licenses today</p>
          
          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Username</label>
              <input
                data-testid="register-username-input"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
                placeholder="Choose a username"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Email</label>
              <input
                data-testid="register-email-input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
                placeholder="Enter your email"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Password</label>
              <input
                data-testid="register-password-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
                placeholder="Create a password (min 6 chars)"
                required
                minLength={6}
              />
            </div>
            <button
              data-testid="register-submit-btn"
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-gradient-to-r from-emerald-600 to-cyan-600 hover:from-emerald-500 hover:to-cyan-500 text-white font-semibold rounded-xl transition-all duration-200 transform hover:scale-[1.02] disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none"
            >
              {loading ? "Creating account..." : "Create Account"}
            </button>
          </form>
          
          <p className="mt-6 text-center text-slate-400">
            Already have an account?{" "}
            <Link to="/login" className="text-emerald-400 hover:text-emerald-300 font-medium transition-colors">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
};

// Sidebar Component
const Sidebar = ({ isOpen, setIsOpen }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const navItems = [
    { icon: BarChart3, label: "Dashboard", path: "/dashboard" },
    { icon: Key, label: "Licenses", path: "/licenses" },
    { icon: History, label: "Validation Logs", path: "/logs" },
    { icon: Shield, label: "Validate License", path: "/validate" },
  ];

  return (
    <>
      {/* Mobile overlay */}
      {isOpen && (
        <div 
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={() => setIsOpen(false)}
        />
      )}
      
      {/* Sidebar */}
      <aside className={`fixed top-0 left-0 z-50 h-full w-72 bg-slate-900 border-r border-slate-800 transform transition-transform duration-300 ease-in-out lg:translate-x-0 ${isOpen ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex flex-col h-full">
          {/* Logo */}
          <div className="flex items-center justify-between p-6 border-b border-slate-800">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-gradient-to-br from-emerald-500 to-cyan-500 rounded-lg">
                <Key className="h-6 w-6 text-white" />
              </div>
              <span className="text-xl font-bold text-white">LicenseVault</span>
            </div>
            <button 
              onClick={() => setIsOpen(false)}
              className="lg:hidden p-2 text-slate-400 hover:text-white"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
          
          {/* Nav Items */}
          <nav className="flex-1 p-4 space-y-2">
            {navItems.map((item) => (
              <Link
                key={item.path}
                to={item.path}
                data-testid={`nav-${item.label.toLowerCase().replace(' ', '-')}`}
                className="flex items-center space-x-3 px-4 py-3 text-slate-300 hover:text-white hover:bg-slate-800/50 rounded-xl transition-all group"
                onClick={() => setIsOpen(false)}
              >
                <item.icon className="h-5 w-5 group-hover:text-emerald-400 transition-colors" />
                <span>{item.label}</span>
              </Link>
            ))}
          </nav>
          
          {/* User Section */}
          <div className="p-4 border-t border-slate-800">
            <div className="flex items-center space-x-3 p-3 bg-slate-800/50 rounded-xl mb-3">
              <div className="h-10 w-10 rounded-full bg-gradient-to-br from-emerald-500 to-cyan-500 flex items-center justify-center">
                <span className="text-white font-semibold">{user?.username?.charAt(0).toUpperCase()}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-white truncate">{user?.username}</p>
                <p className="text-xs text-slate-400 truncate">{user?.email}</p>
              </div>
            </div>
            <button
              data-testid="logout-btn"
              onClick={handleLogout}
              className="w-full flex items-center justify-center space-x-2 px-4 py-2 text-slate-400 hover:text-red-400 hover:bg-red-500/10 rounded-xl transition-all"
            >
              <LogOut className="h-5 w-5" />
              <span>Logout</span>
            </button>
          </div>
        </div>
      </aside>
    </>
  );
};

// Dashboard Layout
const DashboardLayout = ({ children }) => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen bg-slate-950">
      <Sidebar isOpen={sidebarOpen} setIsOpen={setSidebarOpen} />
      
      {/* Main Content */}
      <div className="lg:pl-72">
        {/* Mobile Header */}
        <header className="lg:hidden fixed top-0 left-0 right-0 z-30 bg-slate-900/90 backdrop-blur-lg border-b border-slate-800 px-4 py-3">
          <div className="flex items-center justify-between">
            <button
              onClick={() => setSidebarOpen(true)}
              className="p-2 text-slate-400 hover:text-white"
            >
              <Menu className="h-6 w-6" />
            </button>
            <div className="flex items-center space-x-2">
              <div className="p-1.5 bg-gradient-to-br from-emerald-500 to-cyan-500 rounded-lg">
                <Key className="h-5 w-5 text-white" />
              </div>
              <span className="text-lg font-bold text-white">LicenseVault</span>
            </div>
            <div className="w-10"></div>
          </div>
        </header>
        
        <main className="p-6 pt-20 lg:pt-6">
          {children}
        </main>
      </div>
    </div>
  );
};

// Dashboard Page
const DashboardPage = () => {
  const [stats, setStats] = useState(null);
  const [licenses, setLicenses] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchData();
  }, []);

  const fetchData = async () => {
    try {
      const [statsRes, licensesRes] = await Promise.all([
        axios.get(`${API}/license/stats`),
        axios.get(`${API}/license/all`)
      ]);
      setStats(statsRes.data);
      setLicenses(licensesRes.data);
    } catch (error) {
      toast.error("Failed to fetch dashboard data");
    } finally {
      setLoading(false);
    }
  };

  const StatCard = ({ icon: Icon, label, value, color, subtext }) => (
    <div className="bg-slate-900/50 border border-slate-800 rounded-2xl p-6 hover:border-slate-700 transition-all">
      <div className="flex items-center justify-between mb-4">
        <div className={`p-3 rounded-xl ${color}`}>
          <Icon className="h-6 w-6 text-white" />
        </div>
      </div>
      <p className="text-3xl font-bold text-white mb-1">{value}</p>
      <p className="text-slate-400 text-sm">{label}</p>
      {subtext && <p className="text-xs text-slate-500 mt-1">{subtext}</p>}
    </div>
  );

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-emerald-500"></div>
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="space-y-8">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Dashboard</h1>
          <p className="text-slate-400">Overview of your license management system</p>
        </div>

        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7 gap-4">
          <StatCard
            icon={Key}
            label="Total Licenses"
            value={stats?.total || 0}
            color="bg-gradient-to-br from-slate-600 to-slate-700"
          />
          <StatCard
            icon={CheckCircle}
            label="Active"
            value={stats?.active || 0}
            color="bg-gradient-to-br from-emerald-600 to-emerald-700"
          />
          <StatCard
            icon={Clock}
            label="Expiring Soon"
            value={stats?.expiringSoon || 0}
            color="bg-gradient-to-br from-amber-600 to-amber-700"
            subtext="Within 7 days"
          />
          <StatCard
            icon={AlertTriangle}
            label="Grace Period"
            value={stats?.gracePeriod || 0}
            color="bg-gradient-to-br from-yellow-600 to-yellow-700"
            subtext="Limited access"
          />
          <StatCard
            icon={XCircle}
            label="Expired"
            value={stats?.expired || 0}
            color="bg-gradient-to-br from-red-600 to-red-700"
          />
          <StatCard
            icon={Pause}
            label="Suspended"
            value={stats?.suspended || 0}
            color="bg-gradient-to-br from-orange-600 to-orange-700"
          />
          <StatCard
            icon={XCircle}
            label="Revoked"
            value={stats?.revoked || 0}
            color="bg-gradient-to-br from-rose-600 to-rose-700"
          />
        </div>

        {/* Recent Licenses */}
        <div className="bg-slate-900/50 border border-slate-800 rounded-2xl overflow-hidden">
          <div className="p-6 border-b border-slate-800">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-semibold text-white">Recent Licenses</h2>
              <Link 
                to="/licenses"
                className="text-emerald-400 hover:text-emerald-300 text-sm font-medium transition-colors"
              >
                View all
              </Link>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-slate-800/50">
                <tr>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">License Key</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">User</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Type</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Expiry</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {licenses.slice(0, 5).map((license) => (
                  <tr key={license.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="px-6 py-4">
                      <code className="text-sm text-emerald-400 bg-emerald-500/10 px-2 py-1 rounded">{license.licenseKey}</code>
                    </td>
                    <td className="px-6 py-4">
                      <div>
                        <p className="text-sm font-medium text-white">{license.username}</p>
                        <p className="text-xs text-slate-400">{license.email}</p>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="text-sm text-slate-300">{license.subscriptionType}</span>
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={license.status} />
                    </td>
                    <td className="px-6 py-4">
                      <span className="text-sm text-slate-300">
                        {new Date(license.expiryDate).toLocaleDateString()}
                      </span>
                    </td>
                  </tr>
                ))}
                {licenses.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-6 py-12 text-center text-slate-400">
                      No licenses found. Create your first license to get started.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
};

// Status Badge Component
const StatusBadge = ({ status }) => {
  const styles = {
    ACTIVE: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
    EXPIRED: "bg-red-500/10 text-red-400 border-red-500/20",
    SUSPENDED: "bg-orange-500/10 text-orange-400 border-orange-500/20",
    REVOKED: "bg-rose-500/10 text-rose-400 border-rose-500/20",
    GRACE_PERIOD: "bg-amber-500/10 text-amber-400 border-amber-500/20",
  };

  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${styles[status] || styles.ACTIVE}`}>
      {status === 'GRACE_PERIOD' ? 'GRACE' : status}
    </span>
  );
};

// Licenses Page
const LicensesPage = () => {
  const [licenses, setLicenses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [showRenewModal, setShowRenewModal] = useState(false);
  const [selectedLicense, setSelectedLicense] = useState(null);
  const [searchTerm, setSearchTerm] = useState("");

  useEffect(() => {
    fetchLicenses();
  }, []);

  const fetchLicenses = async () => {
    try {
      const response = await axios.get(`${API}/license/all`);
      setLicenses(response.data);
    } catch (error) {
      toast.error("Failed to fetch licenses");
    } finally {
      setLoading(false);
    }
  };

  const handleSuspend = async (licenseKey) => {
    try {
      await axios.post(`${API}/license/${licenseKey}/suspend`);
      toast.success("License suspended");
      fetchLicenses();
    } catch (error) {
      toast.error(error.response?.data?.error || "Failed to suspend license");
    }
  };

  const handleActivate = async (licenseKey) => {
    try {
      await axios.post(`${API}/license/${licenseKey}/activate`);
      toast.success("License activated");
      fetchLicenses();
    } catch (error) {
      toast.error(error.response?.data?.error || "Failed to activate license");
    }
  };

  const handleRevoke = async (licenseKey) => {
    if (!window.confirm("Are you sure you want to revoke this license? This action cannot be undone.")) return;
    try {
      await axios.post(`${API}/license/${licenseKey}/revoke`);
      toast.success("License revoked");
      fetchLicenses();
    } catch (error) {
      toast.error(error.response?.data?.error || "Failed to revoke license");
    }
  };

  const handleDelete = async (licenseKey) => {
    if (!window.confirm("Are you sure you want to delete this license?")) return;
    try {
      await axios.delete(`${API}/license/${licenseKey}`);
      toast.success("License deleted");
      fetchLicenses();
    } catch (error) {
      toast.error(error.response?.data?.error || "Failed to delete license");
    }
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    toast.success("Copied to clipboard");
  };

  const filteredLicenses = licenses.filter(
    (license) =>
      license.licenseKey.toLowerCase().includes(searchTerm.toLowerCase()) ||
      license.username.toLowerCase().includes(searchTerm.toLowerCase()) ||
      license.email.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-white mb-2">Licenses</h1>
            <p className="text-slate-400">Manage all your license keys</p>
          </div>
          <button
            data-testid="create-license-btn"
            onClick={() => setShowModal(true)}
            className="flex items-center justify-center space-x-2 px-6 py-3 bg-gradient-to-r from-emerald-600 to-cyan-600 hover:from-emerald-500 hover:to-cyan-500 text-white font-semibold rounded-xl transition-all duration-200 transform hover:scale-[1.02]"
          >
            <Plus className="h-5 w-5" />
            <span>Generate License</span>
          </button>
        </div>

        {/* Search */}
        <div className="relative">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-slate-400" />
          <input
            data-testid="license-search-input"
            type="text"
            placeholder="Search by license key, username, or email..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full pl-12 pr-4 py-3 bg-slate-900/50 border border-slate-800 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
          />
        </div>

        {/* Licenses Table */}
        <div className="bg-slate-900/50 border border-slate-800 rounded-2xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-slate-800/50">
                <tr>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">License Key</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">User</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Type</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Status</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Start Date</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Expiry Date</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {loading ? (
                  <tr>
                    <td colSpan={7} className="px-6 py-12 text-center">
                      <div className="flex items-center justify-center">
                        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-emerald-500"></div>
                      </div>
                    </td>
                  </tr>
                ) : filteredLicenses.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-6 py-12 text-center text-slate-400">
                      {searchTerm ? "No licenses match your search." : "No licenses found. Generate your first license to get started."}
                    </td>
                  </tr>
                ) : (
                  filteredLicenses.map((license) => (
                    <tr key={license.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-6 py-4">
                        <div className="flex items-center space-x-2">
                          <code className="text-sm text-emerald-400 bg-emerald-500/10 px-2 py-1 rounded">{license.licenseKey}</code>
                          <button
                            onClick={() => copyToClipboard(license.licenseKey)}
                            className="p-1 text-slate-400 hover:text-white transition-colors"
                            title="Copy license key"
                          >
                            <Copy className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div>
                          <p className="text-sm font-medium text-white">{license.username}</p>
                          <p className="text-xs text-slate-400">{license.email}</p>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm text-slate-300">{license.subscriptionType}</span>
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge status={license.status} />
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm text-slate-300">
                          {new Date(license.startDate).toLocaleDateString()}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm text-slate-300">
                          {new Date(license.expiryDate).toLocaleDateString()}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center space-x-2">
                          <button
                            data-testid={`renew-${license.licenseKey}`}
                            onClick={() => { setSelectedLicense(license); setShowRenewModal(true); }}
                            className="p-2 text-cyan-400 hover:bg-cyan-500/10 rounded-lg transition-colors"
                            title="Renew"
                          >
                            <RefreshCw className="h-4 w-4" />
                          </button>
                          {license.status === "ACTIVE" ? (
                            <button
                              data-testid={`suspend-${license.licenseKey}`}
                              onClick={() => handleSuspend(license.licenseKey)}
                              className="p-2 text-orange-400 hover:bg-orange-500/10 rounded-lg transition-colors"
                              title="Suspend"
                            >
                              <Pause className="h-4 w-4" />
                            </button>
                          ) : license.status === "SUSPENDED" ? (
                            <button
                              data-testid={`activate-${license.licenseKey}`}
                              onClick={() => handleActivate(license.licenseKey)}
                              className="p-2 text-emerald-400 hover:bg-emerald-500/10 rounded-lg transition-colors"
                              title="Activate"
                            >
                              <Play className="h-4 w-4" />
                            </button>
                          ) : null}
                          {license.status !== "REVOKED" && (
                            <button
                              data-testid={`revoke-${license.licenseKey}`}
                              onClick={() => handleRevoke(license.licenseKey)}
                              className="p-2 text-rose-400 hover:bg-rose-500/10 rounded-lg transition-colors"
                              title="Revoke"
                            >
                              <XCircle className="h-4 w-4" />
                            </button>
                          )}
                          <button
                            data-testid={`delete-${license.licenseKey}`}
                            onClick={() => handleDelete(license.licenseKey)}
                            className="p-2 text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
                            title="Delete"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Generate License Modal */}
      {showModal && (
        <GenerateLicenseModal
          onClose={() => setShowModal(false)}
          onSuccess={() => {
            setShowModal(false);
            fetchLicenses();
          }}
        />
      )}

      {/* Renew License Modal */}
      {showRenewModal && selectedLicense && (
        <RenewLicenseModal
          license={selectedLicense}
          onClose={() => { setShowRenewModal(false); setSelectedLicense(null); }}
          onSuccess={() => {
            setShowRenewModal(false);
            setSelectedLicense(null);
            fetchLicenses();
          }}
        />
      )}
    </DashboardLayout>
  );
};

// Generate License Modal
const GenerateLicenseModal = ({ onClose, onSuccess }) => {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [subscriptionType, setSubscriptionType] = useState("MONTHLY");
  const [loading, setLoading] = useState(false);
  const [generatedLicense, setGeneratedLicense] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const response = await axios.post(`${API}/license/generate`, {
        username,
        email,
        subscriptionType,
      });
      setGeneratedLicense(response.data);
      toast.success("License generated successfully!");
    } catch (error) {
      toast.error(error.response?.data?.error || "Failed to generate license");
    } finally {
      setLoading(false);
    }
  };

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    toast.success("Copied to clipboard");
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md shadow-2xl">
        <div className="p-6 border-b border-slate-800">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold text-white">Generate License</h2>
            <button onClick={generatedLicense ? onSuccess : onClose} className="p-2 text-slate-400 hover:text-white">
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>
        
        {generatedLicense ? (
          <div className="p-6 space-y-6">
            <div className="text-center">
              <div className="mx-auto w-16 h-16 bg-emerald-500/10 rounded-full flex items-center justify-center mb-4">
                <CheckCircle className="h-8 w-8 text-emerald-400" />
              </div>
              <h3 className="text-lg font-semibold text-white mb-2">License Generated!</h3>
              <p className="text-slate-400 text-sm">Share this license key with your user</p>
            </div>
            
            <div className="bg-slate-800/50 border border-slate-700 rounded-xl p-4">
              <div className="flex items-center justify-between">
                <code className="text-lg text-emerald-400 font-mono">{generatedLicense.licenseKey}</code>
                <button
                  onClick={() => copyToClipboard(generatedLicense.licenseKey)}
                  className="p-2 text-slate-400 hover:text-white transition-colors"
                >
                  <Copy className="h-5 w-5" />
                </button>
              </div>
            </div>

            <div className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-400">User</span>
                <span className="text-white">{generatedLicense.username}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Email</span>
                <span className="text-white">{generatedLicense.email}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Type</span>
                <span className="text-white">{generatedLicense.subscriptionType}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Expires</span>
                <span className="text-white">{new Date(generatedLicense.expiryDate).toLocaleDateString()}</span>
              </div>
            </div>

            <button
              onClick={onSuccess}
              className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl transition-colors"
            >
              Done
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="p-6 space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Username</label>
              <input
                data-testid="generate-username-input"
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
                placeholder="Enter username"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Email</label>
              <input
                data-testid="generate-email-input"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
                placeholder="Enter email"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Subscription Type</label>
              <select
                data-testid="generate-type-select"
                value={subscriptionType}
                onChange={(e) => setSubscriptionType(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
              >
                <option value="MONTHLY">Monthly</option>
                <option value="QUARTERLY">Quarterly</option>
                <option value="YEARLY">Yearly</option>
              </select>
            </div>
            <div className="flex space-x-3 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 py-3 bg-slate-800 hover:bg-slate-700 text-white font-semibold rounded-xl transition-colors"
              >
                Cancel
              </button>
              <button
                data-testid="generate-submit-btn"
                type="submit"
                disabled={loading}
                className="flex-1 py-3 bg-gradient-to-r from-emerald-600 to-cyan-600 hover:from-emerald-500 hover:to-cyan-500 text-white font-semibold rounded-xl transition-all disabled:opacity-50"
              >
                {loading ? "Generating..." : "Generate"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

// Renew License Modal
const RenewLicenseModal = ({ license, onClose, onSuccess }) => {
  const [subscriptionType, setSubscriptionType] = useState(license.subscriptionType);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      await axios.post(`${API}/license/renew`, {
        licenseKey: license.licenseKey,
        subscriptionType,
      });
      toast.success("License renewed successfully!");
      onSuccess();
    } catch (error) {
      toast.error(error.response?.data?.error || "Failed to renew license");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md shadow-2xl">
        <div className="p-6 border-b border-slate-800">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-semibold text-white">Renew License</h2>
            <button onClick={onClose} className="p-2 text-slate-400 hover:text-white">
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>
        
        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          <div className="bg-slate-800/50 border border-slate-700 rounded-xl p-4">
            <p className="text-slate-400 text-sm mb-1">License Key</p>
            <code className="text-emerald-400 font-mono">{license.licenseKey}</code>
          </div>
          
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-2">New Subscription Type</label>
            <select
              data-testid="renew-type-select"
              value={subscriptionType}
              onChange={(e) => setSubscriptionType(e.target.value)}
              className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
            >
              <option value="MONTHLY">Monthly</option>
              <option value="QUARTERLY">Quarterly</option>
              <option value="YEARLY">Yearly</option>
            </select>
          </div>

          <div className="flex space-x-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-3 bg-slate-800 hover:bg-slate-700 text-white font-semibold rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              data-testid="renew-submit-btn"
              type="submit"
              disabled={loading}
              className="flex-1 py-3 bg-gradient-to-r from-cyan-600 to-emerald-600 hover:from-cyan-500 hover:to-emerald-500 text-white font-semibold rounded-xl transition-all disabled:opacity-50"
            >
              {loading ? "Renewing..." : "Renew License"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// Validation Logs Page
const ValidationLogsPage = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchLogs();
  }, []);

  const fetchLogs = async () => {
    try {
      const response = await axios.get(`${API}/license/validation-logs`);
      setLogs(response.data);
    } catch (error) {
      toast.error("Failed to fetch validation logs");
    } finally {
      setLoading(false);
    }
  };

  const ResultBadge = ({ result }) => {
    const styles = {
      SUCCESS: "bg-emerald-500/10 text-emerald-400 border-emerald-500/20",
      FAILED: "bg-red-500/10 text-red-400 border-red-500/20",
      EXPIRED: "bg-orange-500/10 text-orange-400 border-orange-500/20",
      INVALID: "bg-rose-500/10 text-rose-400 border-rose-500/20",
      SUSPENDED: "bg-amber-500/10 text-amber-400 border-amber-500/20",
    };

    return (
      <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${styles[result] || styles.FAILED}`}>
        {result}
      </span>
    );
  };

  return (
    <DashboardLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Validation Logs</h1>
          <p className="text-slate-400">Recent license validation attempts</p>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-2xl overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-slate-800/50">
                <tr>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">License Key</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Type</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Result</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Message</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">IP Address</th>
                  <th className="px-6 py-4 text-left text-xs font-medium text-slate-400 uppercase tracking-wider">Time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {loading ? (
                  <tr>
                    <td colSpan={6} className="px-6 py-12 text-center">
                      <div className="flex items-center justify-center">
                        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-emerald-500"></div>
                      </div>
                    </td>
                  </tr>
                ) : logs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-6 py-12 text-center text-slate-400">
                      No validation logs found.
                    </td>
                  </tr>
                ) : (
                  logs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="px-6 py-4">
                        <code className="text-sm text-emerald-400 bg-emerald-500/10 px-2 py-1 rounded">{log.licenseKey}</code>
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm text-slate-300">{log.validationType}</span>
                      </td>
                      <td className="px-6 py-4">
                        <ResultBadge result={log.result} />
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm text-slate-400">{log.message}</span>
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm text-slate-300">{log.ipAddress}</span>
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-sm text-slate-300">
                          {new Date(log.validatedAt).toLocaleString()}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
};

// Validate License Page
const ValidateLicensePage = () => {
  const [licenseKey, setLicenseKey] = useState("");
  const [machineId, setMachineId] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [validationType, setValidationType] = useState("online");

  const handleValidate = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);
    
    try {
      const endpoint = validationType === "online" ? "/license/validate" : "/license/validate-offline";
      const response = await axios.post(`${API}${endpoint}`, {
        licenseKey: licenseKey.trim().toUpperCase(),
        machineId: machineId || undefined,
      });
      setResult(response.data);
    } catch (error) {
      toast.error(error.response?.data?.error || "Validation failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <DashboardLayout>
      <div className="max-w-2xl mx-auto space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-white mb-2">Validate License</h1>
          <p className="text-slate-400">Check if a license key is valid</p>
        </div>

        <div className="bg-slate-900/50 border border-slate-800 rounded-2xl p-6">
          <form onSubmit={handleValidate} className="space-y-5">
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">License Key</label>
              <input
                data-testid="validate-license-input"
                type="text"
                value={licenseKey}
                onChange={(e) => setLicenseKey(e.target.value.toUpperCase())}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent font-mono tracking-wider"
                placeholder="XXXX-XXXX-XXXX-XXXX"
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Machine ID (Optional)</label>
              <input
                data-testid="validate-machine-input"
                type="text"
                value={machineId}
                onChange={(e) => setMachineId(e.target.value)}
                className="w-full px-4 py-3 bg-slate-800/50 border border-slate-700 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
                placeholder="Enter machine identifier"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-300 mb-2">Validation Type</label>
              <div className="flex space-x-4">
                <label className="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="radio"
                    name="validationType"
                    value="online"
                    checked={validationType === "online"}
                    onChange={(e) => setValidationType(e.target.value)}
                    className="text-emerald-500 focus:ring-emerald-500"
                  />
                  <span className="text-slate-300">Online (Server)</span>
                </label>
                <label className="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="radio"
                    name="validationType"
                    value="offline"
                    checked={validationType === "offline"}
                    onChange={(e) => setValidationType(e.target.value)}
                    className="text-emerald-500 focus:ring-emerald-500"
                  />
                  <span className="text-slate-300">Offline (Local)</span>
                </label>
              </div>
            </div>

            <button
              data-testid="validate-submit-btn"
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-gradient-to-r from-emerald-600 to-cyan-600 hover:from-emerald-500 hover:to-cyan-500 text-white font-semibold rounded-xl transition-all duration-200 transform hover:scale-[1.02] disabled:opacity-50 disabled:transform-none"
            >
              {loading ? "Validating..." : "Validate License"}
            </button>
          </form>
        </div>

        {/* Result */}
        {result && (
          <div className={`bg-slate-900/50 border rounded-2xl p-6 ${result.valid ? 'border-emerald-500/30' : 'border-red-500/30'}`}>
            <div className="flex items-center space-x-3 mb-4">
              {result.valid ? (
                <div className="p-2 bg-emerald-500/10 rounded-full">
                  <CheckCircle className="h-6 w-6 text-emerald-400" />
                </div>
              ) : (
                <div className="p-2 bg-red-500/10 rounded-full">
                  <XCircle className="h-6 w-6 text-red-400" />
                </div>
              )}
              <div>
                <h3 className={`text-lg font-semibold ${result.valid ? 'text-emerald-400' : 'text-red-400'}`}>
                  {result.valid ? "License Valid" : "License Invalid"}
                </h3>
                <p className="text-slate-400 text-sm">{result.message}</p>
              </div>
            </div>

            {result.warningMessage && (
              <div className="mb-4 p-3 bg-amber-500/10 border border-amber-500/20 rounded-xl">
                <div className="flex items-center space-x-2">
                  <AlertTriangle className="h-5 w-5 text-amber-400" />
                  <p className="text-amber-400 text-sm">{result.warningMessage}</p>
                </div>
              </div>
            )}

            {result.licenseKey && (
              <div className="space-y-3 text-sm">
                {result.status && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Status</span>
                    <StatusBadge status={result.status} />
                  </div>
                )}
                {result.subscriptionType && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Subscription</span>
                    <span className="text-white">{result.subscriptionType}</span>
                  </div>
                )}
                {result.startDate && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Start Date</span>
                    <span className="text-white">{new Date(result.startDate).toLocaleDateString()}</span>
                  </div>
                )}
                {result.expiryDate && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Expiry Date</span>
                    <span className="text-white">{new Date(result.expiryDate).toLocaleDateString()}</span>
                  </div>
                )}
                {result.daysUntilExpiry !== undefined && result.valid && (
                  <div className="flex justify-between">
                    <span className="text-slate-400">Days Until Expiry</span>
                    <span className={`font-medium ${result.daysUntilExpiry <= 7 ? 'text-amber-400' : 'text-white'}`}>
                      {result.daysUntilExpiry} days
                    </span>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </DashboardLayout>
  );
};

function App() {
  return (
    <AuthProvider>
      <div className="App">
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
            <Route path="/licenses" element={<ProtectedRoute><LicensesPage /></ProtectedRoute>} />
            <Route path="/logs" element={<ProtectedRoute><ValidationLogsPage /></ProtectedRoute>} />
            <Route path="/validate" element={<ProtectedRoute><ValidateLicensePage /></ProtectedRoute>} />
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </BrowserRouter>
        <Toaster 
          position="top-right" 
          toastOptions={{
            style: {
              background: '#1e293b',
              color: '#fff',
              border: '1px solid #334155',
            },
          }}
        />
      </div>
    </AuthProvider>
  );
}

export default App;
