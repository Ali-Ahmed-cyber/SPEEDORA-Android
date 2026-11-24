import React, { useEffect, useMemo, useState } from 'react';
import {
  Check,
  CheckSquare,
  Copy,
  Download,
  FileCode2,
  FolderTree,
  Layers,
  Search,
  ShieldCheck,
  Square,
} from 'lucide-react';
import {
  SpeedoraBrandEmblemSvg,
  SpeedoraLauncherSvg,
  SpeedoraNavIconSvg,
  SpeedoraStartupEmblemSvg,
  SpeedoraTransferEmblemSvg,
} from './components/SpeedoraVectors';

interface ProjectFileEntry {
  path: string;
  name: string;
  sizeBytes: number;
  category: string;
}

interface ProjectTreeResponse {
  packageName: string;
  minSdk: number;
  targetSdk: number;
  fileCount: number;
  totalBytes: number;
  files: ProjectFileEntry[];
}

type Lang = 'ar' | 'en';
type ActiveSection = 'overview' | 'explorer' | 'security' | 'qa';

const QA_ITEMS = [
  {
    id: 'qa-versions',
    ar: 'اختبار التوافق على جهازين: Android 13–15 وجهاز Android 8.0–12 من مصنّعين مختلفين',
    en: 'Cross-version check on two phones: Android 13–15 and Android 8.0–12 from distinct OEMs',
  },
  {
    id: 'qa-perms',
    ar: 'رفض إذن الوسائط مرة ثم منحه: التحقق من الشرح العربي وعدم تعطل التطبيق',
    en: 'Media permission denial & recovery: verify Arabic rationale and zero crashes',
  },
  {
    id: 'qa-wifidirect',
    ar: 'اكتشاف ونقل مباشر عبر Wi-Fi Direct بين الجهازين بدون اتصال إنترنت',
    en: 'Direct Wi-Fi Direct discovery and transfer between two devices with no internet',
  },
  {
    id: 'qa-nsd-qr',
    ar: 'اكتشاف NSD (_speedora._tcp.) واقتران رمز QR المؤقت مع مطابقة رمز SAS من 6 أرقام',
    en: 'NSD local Wi-Fi discovery and temporary QR pairing with matching 6-digit SAS verification',
  },
  {
    id: 'qa-large',
    ar: 'نقل ملف كبير (>1 GB) والتحقق من استقرار الذاكرة ودقة السرعة المقاسة فعلياً (MB/s)',
    en: 'Transfer >1 GB file verifying low RAM streaming and genuine measured MB/s speed',
  },
  {
    id: 'qa-multi',
    ar: 'تحديد ملفات متعددة عبر الصور والمستندات والتطبيقات وإرسالها في جلسة واحدة',
    en: 'Multi-category selection across Photos, Documents, and exportable APKs in one session',
  },
  {
    id: 'qa-interrupt',
    ar: 'إيقاف مؤقت / استئناف / إلغاء / رفض المستلم مع التأكد من عدم حفظ ملفات ناقصة',
    en: 'Pause, Resume, Cancel, and Receiver Rejection confirming no partial file is committed',
  },
  {
    id: 'qa-battery',
    ar: 'اختبار وضع توفير البطارية، تقليل الحركة، وانخفاض مساحة التخزين',
    en: 'Verify Battery-Saving mode, Reduced Motion, and low-storage handling',
  },
];

const COPY = {
  ar: {
    slogan: 'نقل أسرع. اتصال أذكى.',
    navOverview: 'نظرة عامة',
    navExplorer: 'مستكشف الكود',
    navSecurity: 'البروتوكول والأمان',
    navQa: 'فحص الأجهزة',
    downloadZip: 'تنزيل مشروع Android',
    heroTitle: 'مشروع SPEEDORA الأصلي للأندرويد (Kotlin و Jetpack Compose)',
    heroBody:
      'هذه الواجهة مخصصة لاستعراض وتنزيل الشيفرة المصدرية الحقيقية لتطبيق أندرويد نيتيف com.speedora.app المبني في مجلد android/، وليست بديلاً ويب عن التطبيق. تتضمن الحزمة محرك نقل مشفر ثنائي، اكتشاف Wi-Fi Direct و NSD، متصفح ملفات حقيقي بـ 10 فئات، واختبارات آلية.',
    honestTitle: 'حالة التنفيذ والبناء والاختبار بشفافية',
    colLayer: 'المكوّن / المرحلة',
    colStatus: 'الحالة الفعلية',
    colDetail: 'التفاصيل التقنية',
    explorerSearch: 'ابحث في ملفات Kotlin و Gradle و XML…',
    allFilter: 'الكل',
    copyCode: 'نسخ الملف',
    copied: 'تم النسخ',
    loadingTree: 'جاري قراءة ملفات المشروع من القرص…',
    loadingFile: 'جاري تحميل محتوى الملف…',
    vectorTitle: 'منظومة الشعارات المتجهة الأصلية (5 أصول)',
    protocolTitle: 'تشريح إطار النقل المشفر SPDR v1',
    qaTitle: 'قائمة التحقق على جهازين فعليين',
    qaSubtitle: 'يُعلَّم كل بند فقط بعد تشغيل الاختبار فعلياً على هاتفين أندرويد.',
  },
  en: {
    slogan: 'FASTER. SMARTER. CONNECTED.',
    navOverview: 'Overview',
    navExplorer: 'Source Explorer',
    navSecurity: 'Protocol & Crypto',
    navQa: 'Device QA',
    downloadZip: 'Download Android Project',
    heroTitle: 'SPEEDORA Native Android Studio Codebase (Kotlin & Jetpack Compose)',
    heroBody:
      'This portal inspects and packages the genuine native Android project com.speedora.app located in /android. It is a source explorer rather than a web replacement for the APK, exposing the binary encrypted transfer engine, Wi-Fi Direct / NSD discovery, 10-category MediaStore/SAF browser, and automated test suite.',
    honestTitle: 'Transparent Implementation, Build & Verification Matrix',
    colLayer: 'Component / Gate',
    colStatus: 'Actual Status',
    colDetail: 'Engineering Notes',
    explorerSearch: 'Search Kotlin, Gradle, and XML files…',
    allFilter: 'All',
    copyCode: 'Copy File',
    copied: 'Copied',
    loadingTree: 'Reading workspace Android source files…',
    loadingFile: 'Loading file contents…',
    vectorTitle: 'Original SPEEDORA Vector Brand System (5 Assets)',
    protocolTitle: 'Encrypted Framed Protocol SPDR v1 Anatomy',
    qaTitle: 'Two-Device Physical Hardware QA Checklist',
    qaSubtitle: 'Check items only after physically executing them across two Android devices.',
  },
};

export default function App() {
  const [lang, setLang] = useState<Lang>('ar');
  const [section, setSection] = useState<ActiveSection>('overview');
  const [tree, setTree] = useState<ProjectTreeResponse | null>(null);
  const [treeError, setTreeError] = useState<string | null>(null);
  const [selectedPath, setSelectedPath] = useState<string>('android/app/src/main/java/com/speedora/app/transfer/TransferEngine.kt');
  const [fileContent, setFileContent] = useState<string>('');
  const [loadingFile, setLoadingFile] = useState<boolean>(false);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [copied, setCopied] = useState<boolean>(false);
  const [qaChecked, setQaChecked] = useState<Record<string, boolean>>(() => {
    try {
      const saved = localStorage.getItem('speedora_qa_checks');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });

  const t = COPY[lang];

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
  }, [lang]);

  useEffect(() => {
    fetch('/api/project/tree')
      .then((r) => {
        if (!r.ok) throw new Error('Failed to load project tree');
        return r.json();
      })
      .then((data: ProjectTreeResponse) => {
        setTree(data);
        if (data.files.length > 0 && !data.files.some((f) => f.path === selectedPath)) {
          setSelectedPath(data.files[0].path);
        }
      })
      .catch((err) => setTreeError(err.message));
  }, []);

  useEffect(() => {
    if (!selectedPath) return;
    setLoadingFile(true);
    fetch(`/api/project/file?path=${encodeURIComponent(selectedPath)}`)
      .then((r) => r.json())
      .then((data) => {
        setFileContent(data.content ?? '');
      })
      .catch(() => setFileContent(''))
      .finally(() => setLoadingFile(false));
  }, [selectedPath]);

  const categories = useMemo(() => {
    if (!tree) return [];
    const set = new Set(tree.files.map((f) => f.category));
    return ['all', ...Array.from(set).sort()];
  }, [tree]);

  const filteredFiles = useMemo(() => {
    if (!tree) return [];
    const q = searchQuery.trim().toLowerCase();
    return tree.files.filter((f) => {
      const matchCat = categoryFilter === 'all' || f.category === categoryFilter;
      const matchQ = !q || f.path.toLowerCase().includes(q) || f.name.toLowerCase().includes(q);
      return matchCat && matchQ;
    });
  }, [tree, searchQuery, categoryFilter]);

  const handleCopy = () => {
    if (!fileContent) return;
    navigator.clipboard?.writeText(fileContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 1600);
  };

  const toggleQa = (id: string) => {
    setQaChecked((prev) => {
      const next = { ...prev, [id]: !prev[id] };
      try {
        localStorage.setItem('speedora_qa_checks', JSON.stringify(next));
      } catch {
        // ignore storage quota errors
      }
      return next;
    });
  };

  const statusRows = useMemo(
    () => [
      {
        layer: lang === 'ar' ? 'مشروع Android Studio والكود المصدري (Kotlin + Compose)' : 'Android Studio Project & Kotlin/Compose Source',
        status: lang === 'ar' ? 'مُنفّذ بالكامل' : 'Implemented',
        tone: 'text-[#15803D]',
        detail: tree
          ? `${tree.fileCount} files · ${(tree.totalBytes / 1024).toFixed(1)} KB · minSdk ${tree.minSdk} / targetSdk ${tree.targetSdk}`
          : 'com.speedora.app',
      },
      {
        layer: lang === 'ar' ? 'نواة التشفير والبروتوكول والاختبارات الآلية' : 'Crypto, Binary Protocol & Automated Unit/Integration Tests',
        status: lang === 'ar' ? 'مُنفّذ بالكامل' : 'Implemented',
        tone: 'text-[#15803D]',
        detail:
          lang === 'ar'
            ? '6 ملفات اختبار JVM + اختبار TCP Loopback + اختبار Room Instrumentation'
            : '6 JVM unit/loopback suites + Room Android instrumentation suite',
      },
      {
        layer: lang === 'ar' ? 'بناء Android APK وتنفيذ اختبارات Gradle' : 'Android APK Compilation & Gradle Test Execution',
        status: lang === 'ar' ? 'لم يتم التحقق هنا (جاهز عبر GitHub Actions)' : 'Not Yet Verified Locally (CI Ready)',
        tone: 'text-[#B45309]',
        detail:
          lang === 'ar'
            ? 'حاوية التشغيل الحالية Node.js فقط بدون JDK 17 أو Android SDK؛ أُرفق مسار .github/workflows/android-build.yml لبناء APK وإصداره تلقائياً.'
            : 'Current container is Node-only without JDK 17 or Android SDK; .github/workflows/android-build.yml builds & publishes the debug APK.',
      },
      {
        layer: lang === 'ar' ? 'اختبار جهازين أندرويد فعليين (Wi-Fi Direct / NSD / QR)' : 'Physical Two-Device Hardware Testing',
        status: lang === 'ar' ? 'غير مُختبر فعلياً بعد' : 'Not Tested on Physical Devices',
        tone: 'text-[#6B809B]',
        detail:
          lang === 'ar'
            ? 'يتطلب هاتفين أندرويد فعليين عبر قائمة الفحص في android/TESTING_CHECKLIST.md.'
            : 'Requires two physical Android phones following android/TESTING_CHECKLIST.md.',
      },
    ],
    [lang, tree]
  );

  return (
    <div className="min-h-screen bg-[#FFFFFF] text-[#102B50] flex flex-col">
      {/* Strict 3-Zone Top Bar */}
      <header className="border-b border-[#DCE6F4] bg-[#FFFFFF] sticky top-0 z-30">
        <div className="max-w-[1360px] mx-auto px-6 h-16 flex items-center justify-between gap-8">
          {/* Zone 1: Single-element brand wordmark */}
          <button
            type="button"
            onClick={() => setSection('overview')}
            className="flex items-center gap-2.5 text-lg font-extrabold tracking-tight text-[#102B50] whitespace-nowrap shrink-0 cursor-pointer"
          >
            <SpeedoraNavIconSvg size={24} />
            <span>SPEEDORA</span>
          </button>

          {/* Zone 2: 4 concise single-line nav links */}
          <nav className="hidden md:flex items-center gap-7 text-sm font-semibold text-[#4B6280]">
            <button
              type="button"
              onClick={() => setSection('overview')}
              className={`whitespace-nowrap shrink-0 pb-0.5 border-b-2 transition-colors cursor-pointer ${
                section === 'overview'
                  ? 'border-[#1769D2] text-[#102B50]'
                  : 'border-transparent hover:text-[#1769D2]'
              }`}
            >
              {t.navOverview}
            </button>
            <button
              type="button"
              onClick={() => setSection('explorer')}
              className={`whitespace-nowrap shrink-0 pb-0.5 border-b-2 transition-colors cursor-pointer ${
                section === 'explorer'
                  ? 'border-[#1769D2] text-[#102B50]'
                  : 'border-transparent hover:text-[#1769D2]'
              }`}
            >
              {t.navExplorer}
            </button>
            <button
              type="button"
              onClick={() => setSection('security')}
              className={`whitespace-nowrap shrink-0 pb-0.5 border-b-2 transition-colors cursor-pointer ${
                section === 'security'
                  ? 'border-[#1769D2] text-[#102B50]'
                  : 'border-transparent hover:text-[#1769D2]'
              }`}
            >
              {t.navSecurity}
            </button>
            <button
              type="button"
              onClick={() => setSection('qa')}
              className={`whitespace-nowrap shrink-0 pb-0.5 border-b-2 transition-colors cursor-pointer ${
                section === 'qa'
                  ? 'border-[#1769D2] text-[#102B50]'
                  : 'border-transparent hover:text-[#1769D2]'
              }`}
            >
              {t.navQa}
            </button>
          </nav>

          {/* Zone 3: 1 primary CTA */}
          <a
            href="/api/project/download.zip"
            download="speedora-android-project.zip"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-[#1769D2] hover:bg-[#104FA8] text-[#FFFFFF] text-sm font-semibold transition-colors whitespace-nowrap shrink-0"
          >
            <Download className="w-4 h-4" />
            <span>{t.downloadZip}</span>
          </a>
        </div>
      </header>

      {/* Workspace Sub-toolbar (Language + Mobile Section Switch) */}
      <div className="bg-[#F4F7FC] border-b border-[#DCE6F4]">
        <div className="max-w-[1360px] mx-auto px-6 py-2.5 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3 text-xs text-[#4B6280] font-medium tabular-nums">
            <span>com.speedora.app</span>
            <span aria-hidden="true">·</span>
            <span>{t.slogan}</span>
            {tree && (
              <>
                <span aria-hidden="true">·</span>
                <span>
                  {tree.fileCount} {lang === 'ar' ? 'ملفاً مصدرياً' : 'source files'}
                </span>
              </>
            )}
          </div>

          <div className="flex items-center gap-2">
            <div className="flex md:hidden items-center gap-1 bg-[#FFFFFF] border border-[#DCE6F4] rounded-lg p-0.5">
              {(['overview', 'explorer', 'security', 'qa'] as ActiveSection[]).map((sec) => (
                <button
                  key={sec}
                  type="button"
                  onClick={() => setSection(sec)}
                  className={`px-2.5 py-1 rounded-md text-xs font-semibold whitespace-nowrap ${
                    section === sec ? 'bg-[#1769D2] text-white' : 'text-[#4B6280]'
                  }`}
                >
                  {sec === 'overview'
                    ? t.navOverview
                    : sec === 'explorer'
                    ? t.navExplorer
                    : sec === 'security'
                    ? t.navSecurity
                    : t.navQa}
                </button>
              ))}
            </div>

            <div className="flex items-center bg-[#FFFFFF] border border-[#DCE6F4] rounded-lg p-0.5">
              <button
                type="button"
                onClick={() => setLang('ar')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors cursor-pointer ${
                  lang === 'ar' ? 'bg-[#1769D2] text-white' : 'text-[#4B6280] hover:text-[#102B50]'
                }`}
              >
                العربية RTL
              </button>
              <button
                type="button"
                onClick={() => setLang('en')}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors cursor-pointer ${
                  lang === 'en' ? 'bg-[#1769D2] text-white' : 'text-[#4B6280] hover:text-[#102B50]'
                }`}
              >
                English LTR
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <main className="flex-1 max-w-[1360px] w-full mx-auto px-6 py-8">
        {section === 'overview' && (
          <div className="space-y-10">
            {/* Hero Summary */}
            <section className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
              <div className="lg:col-span-8 bg-[#F4F7FC] border border-[#DCE6F4] rounded-2xl p-8 flex flex-col justify-between">
                <div className="space-y-4">
                  <div className="flex items-center gap-3">
                    <SpeedoraBrandEmblemSvg size={42} />
                    <span className="text-sm font-semibold text-[#1769D2]">{t.slogan}</span>
                  </div>
                  <h1 className="text-2xl md:text-3xl font-extrabold text-[#102B50] leading-snug max-w-2xl">
                    {t.heroTitle}
                  </h1>
                  <p className="text-[15px] leading-relaxed text-[#4B6280] max-w-2xl">
                    {t.heroBody}
                  </p>
                </div>
                <div className="pt-6 mt-6 border-t border-[#DCE6F4] flex flex-wrap items-center gap-4">
                  <button
                    type="button"
                    onClick={() => setSection('explorer')}
                    className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#1769D2] text-white text-sm font-semibold hover:bg-[#104FA8] transition-colors cursor-pointer"
                  >
                    <FolderTree className="w-4 h-4" />
                    <span>{t.navExplorer}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setSection('security')}
                    className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-[#FFFFFF] border border-[#DCE6F4] text-[#102B50] text-sm font-semibold hover:bg-[#EAF3FF] transition-colors cursor-pointer"
                  >
                    <ShieldCheck className="w-4 h-4 text-[#1769D2]" />
                    <span>{t.navSecurity}</span>
                  </button>
                </div>
              </div>

              <div className="lg:col-span-4 bg-[#FFFFFF] border border-[#DCE6F4] rounded-2xl p-6 flex flex-col justify-between">
                <div className="space-y-4">
                  <h2 className="text-base font-bold text-[#102B50]">
                    {lang === 'ar' ? 'المواصفات الهندسية' : 'Architecture Specifications'}
                  </h2>
                  <dl className="space-y-3 text-sm tabular-nums">
                    <div className="flex justify-between py-1.5 border-b border-[#F4F7FC]">
                      <dt className="text-[#4B6280]">Package ID</dt>
                      <dd className="font-mono font-semibold text-[#102B50]">com.speedora.app</dd>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-[#F4F7FC]">
                      <dt className="text-[#4B6280]">Min / Target SDK</dt>
                      <dd className="font-mono font-semibold text-[#102B50]">26 (8.0) / 35 (15)</dd>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-[#F4F7FC]">
                      <dt className="text-[#4B6280]">UI Toolkit</dt>
                      <dd className="font-semibold text-[#102B50]">Jetpack Compose M3</dd>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-[#F4F7FC]">
                      <dt className="text-[#4B6280]">Key Agreement</dt>
                      <dd className="font-mono font-semibold text-[#102B50]">ECDH P-256 + HKDF</dd>
                    </div>
                    <div className="flex justify-between py-1.5 border-b border-[#F4F7FC]">
                      <dt className="text-[#4B6280]">Cipher &amp; Integrity</dt>
                      <dd className="font-mono font-semibold text-[#102B50]">AES-256-GCM / SHA-256</dd>
                    </div>
                    <div className="flex justify-between py-1.5">
                      <dt className="text-[#4B6280]">Cloud Dependency</dt>
                      <dd className="font-semibold text-[#15803D]">
                        {lang === 'ar' ? 'صفر (محلي 100%)' : 'Zero (100% Offline)'}
                      </dd>
                    </div>
                  </dl>
                </div>
              </div>
            </section>

            {/* Transparent Verification Matrix */}
            <section className="space-y-4">
              <h2 className="text-xl font-bold text-[#102B50]">{t.honestTitle}</h2>
              {treeError && (
                <div className="p-4 rounded-xl bg-[#FEE2E2] text-[#DC2626] text-sm">{treeError}</div>
              )}
              <div className="border border-[#DCE6F4] rounded-2xl overflow-hidden bg-[#FFFFFF]">
                <table className="w-full text-sm">
                  <thead className="bg-[#F4F7FC] border-b border-[#DCE6F4] text-[#4B6280]">
                    <tr>
                      <th className="text-start px-5 py-3.5 font-semibold">{t.colLayer}</th>
                      <th className="text-start px-5 py-3.5 font-semibold">{t.colStatus}</th>
                      <th className="text-start px-5 py-3.5 font-semibold">{t.colDetail}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#F4F7FC]">
                    {statusRows.map((row) => (
                      <tr key={row.layer} className="hover:bg-[#F4F7FC]/60">
                        <td className="px-5 py-4 font-semibold text-[#102B50]">{row.layer}</td>
                        <td className={`px-5 py-4 font-bold whitespace-nowrap ${row.tone}`}>
                          {row.status}
                        </td>
                        <td className="px-5 py-4 text-[#4B6280] tabular-nums">{row.detail}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        )}

        {section === 'explorer' && (
          <section className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
            {/* Left/Right Sidebar: File Tree */}
            <aside className="lg:col-span-4 border border-[#DCE6F4] rounded-2xl bg-[#FFFFFF] overflow-hidden">
              <div className="p-4 border-b border-[#DCE6F4] space-y-3 bg-[#F4F7FC]">
                <div className="relative">
                  <Search className="w-4 h-4 text-[#6B809B] absolute top-3 start-3" />
                  <input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder={t.explorerSearch}
                    className="w-full ps-9 pe-3 py-2 text-sm bg-[#FFFFFF] border border-[#DCE6F4] rounded-xl focus:outline-none focus:border-[#1769D2]"
                  />
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {categories.map((cat) => (
                    <button
                      key={cat}
                      type="button"
                      onClick={() => setCategoryFilter(cat)}
                      className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors cursor-pointer ${
                        categoryFilter === cat
                          ? 'bg-[#1769D2] text-white'
                          : 'bg-[#FFFFFF] text-[#4B6280] border border-[#DCE6F4] hover:text-[#102B50]'
                      }`}
                    >
                      {cat === 'all' ? t.allFilter : cat}
                    </button>
                  ))}
                </div>
              </div>

              <div className="max-h-[620px] overflow-y-auto divide-y divide-[#F4F7FC]">
                {!tree && <p className="p-4 text-sm text-[#4B6280]">{t.loadingTree}</p>}
                {filteredFiles.map((file) => {
                  const active = file.path === selectedPath;
                  return (
                    <button
                      key={file.path}
                      type="button"
                      onClick={() => setSelectedPath(file.path)}
                      className={`w-full text-start px-4 py-3 flex items-center justify-between gap-3 transition-colors cursor-pointer ${
                        active ? 'bg-[#EAF3FF] text-[#102B50]' : 'hover:bg-[#F4F7FC]'
                      }`}
                    >
                      <div className="min-w-0 flex items-center gap-2.5">
                        <FileCode2 className={`w-4 h-4 shrink-0 ${active ? 'text-[#1769D2]' : 'text-[#6B809B]'}`} />
                        <div className="min-w-0">
                          <div className="text-sm font-semibold truncate">{file.name}</div>
                          <div className="text-[11px] font-mono text-[#6B809B] truncate" dir="ltr">
                            {file.path}
                          </div>
                        </div>
                      </div>
                      <span className="text-xs font-mono text-[#4B6280] tabular-nums shrink-0">
                        {(file.sizeBytes / 1024).toFixed(1)}K
                      </span>
                    </button>
                  );
                })}
              </div>
            </aside>

            {/* Code Viewer */}
            <div className="lg:col-span-8 border border-[#DCE6F4] rounded-2xl bg-[#FFFFFF] overflow-hidden">
              <div className="px-5 py-3.5 bg-[#F4F7FC] border-b border-[#DCE6F4] flex items-center justify-between gap-4">
                <span className="font-mono text-xs text-[#102B50] truncate" dir="ltr">
                  {selectedPath}
                </span>
                <button
                  type="button"
                  onClick={handleCopy}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#FFFFFF] border border-[#DCE6F4] text-xs font-semibold text-[#102B50] hover:bg-[#EAF3FF] whitespace-nowrap shrink-0 cursor-pointer"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-[#15803D]" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copied ? t.copied : t.copyCode}</span>
                </button>
              </div>
              <div className="max-h-[660px] overflow-auto bg-[#0B1524] text-[#EAF3FF] p-5" dir="ltr">
                {loadingFile ? (
                  <p className="text-sm text-[#A8BDD9]">{t.loadingFile}</p>
                ) : (
                  <pre className="text-[13px] leading-relaxed font-mono whitespace-pre">{fileContent}</pre>
                )}
              </div>
            </div>
          </section>
        )}

        {section === 'security' && (
          <div className="space-y-10">
            {/* 5 Original Vector Assets */}
            <section className="space-y-4">
              <h2 className="text-xl font-bold text-[#102B50]">{t.vectorTitle}</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
                {[
                  {
                    title: lang === 'ar' ? '1. أيقونة التطبيق' : '1. Launcher Icon',
                    svg: <SpeedoraLauncherSvg size={68} />,
                  },
                  {
                    title: lang === 'ar' ? '2. الشعار الرئيسي' : '2. Main Brand Mark',
                    svg: <SpeedoraBrandEmblemSvg size={64} />,
                  },
                  {
                    title: lang === 'ar' ? '3. شعار الإقلاع' : '3. Startup Emblem',
                    svg: <SpeedoraStartupEmblemSvg size={68} />,
                  },
                  {
                    title: lang === 'ar' ? '4. شعار النقل' : '4. Transfer Emblem',
                    svg: <SpeedoraTransferEmblemSvg size={68} />,
                  },
                  {
                    title: lang === 'ar' ? '5. أيقونة التنقل' : '5. Small Nav Icon',
                    svg: <SpeedoraNavIconSvg size={44} />,
                  },
                ].map((item) => (
                  <div
                    key={item.title}
                    className="border border-[#DCE6F4] rounded-2xl p-5 bg-[#FFFFFF] flex flex-col items-center gap-4 text-center"
                  >
                    <div className="h-20 flex items-center justify-center">{item.svg}</div>
                    <span className="text-sm font-semibold text-[#102B50]">{item.title}</span>
                  </div>
                ))}
              </div>
            </section>

            {/* Framed Protocol Anatomy */}
            <section className="space-y-4">
              <h2 className="text-xl font-bold text-[#102B50]">{t.protocolTitle}</h2>
              <div className="border border-[#DCE6F4] rounded-2xl p-6 bg-[#F4F7FC] space-y-6">
                <div className="grid grid-cols-2 md:grid-cols-5 gap-3" dir="ltr">
                  {[
                    { field: 'Magic', bytes: '4 Bytes', val: '0x53504452 ("SPDR")' },
                    { field: 'Version', bytes: '1 Byte', val: '0x01' },
                    { field: 'FrameType', bytes: '1 Byte', val: 'HELLO .. ERROR' },
                    { field: 'Sequence', bytes: '8 Bytes', val: 'Monotonic u64' },
                    { field: 'Payload', bytes: '4B Len + Body', val: 'AES-256-GCM + 16B Tag' },
                  ].map((seg) => (
                    <div key={seg.field} className="bg-[#FFFFFF] border border-[#DCE6F4] rounded-xl p-4">
                      <div className="text-xs font-mono text-[#1769D2] font-semibold">{seg.bytes}</div>
                      <div className="text-base font-bold text-[#102B50] mt-1">{seg.field}</div>
                      <div className="text-xs font-mono text-[#4B6280] mt-1">{seg.val}</div>
                    </div>
                  ))}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                  <div className="bg-[#FFFFFF] border border-[#DCE6F4] rounded-xl p-4 space-y-1.5">
                    <div className="flex items-center gap-2 font-bold text-[#102B50]">
                      <Layers className="w-4 h-4 text-[#1769D2]" />
                      <span>1. Ephemeral ECDH P-256 + HKDF</span>
                    </div>
                    <p className="text-[#4B6280] leading-relaxed">
                      {lang === 'ar'
                        ? 'توليد زوج مفاتيح مؤقت لكل جلسة على منحنى secp256r1 واشتقاق مفاتيح اتجاهية منفصلة ورمز SAS من 6 أرقام.'
                        : 'Generates fresh secp256r1 keys per session, deriving directional AES-256 keys and a 6-digit SAS code.'}
                    </p>
                  </div>
                  <div className="bg-[#FFFFFF] border border-[#DCE6F4] rounded-xl p-4 space-y-1.5">
                    <div className="flex items-center gap-2 font-bold text-[#102B50]">
                      <ShieldCheck className="w-4 h-4 text-[#1769D2]" />
                      <span>2. Unique Nonces + AAD Binding</span>
                    </div>
                    <p className="text-[#4B6280] leading-relaxed">
                      {lang === 'ar'
                        ? 'كل كتلة مشفرة تستخدم Nonce من 12 بايت (بادئة عشوائية + عداد تسلسلي صارم) مع ربط رقم الجلسة والكتلة بـ AAD.'
                        : 'Each encrypted chunk uses a 12-byte nonce (random prefix + strict counter) and authenticates session + chunk index via AAD.'}
                    </p>
                  </div>
                  <div className="bg-[#FFFFFF] border border-[#DCE6F4] rounded-xl p-4 space-y-1.5">
                    <div className="flex items-center gap-2 font-bold text-[#102B50]">
                      <Check className="w-4 h-4 text-[#1769D2]" />
                      <span>3. Atomic Commit After SHA-256</span>
                    </div>
                    <p className="text-[#4B6280] leading-relaxed">
                      {lang === 'ar'
                        ? 'لا يُعتمد الملف المستلم ولا يُحفظ في التنزيلات النهائية إلا بعد مطابقة بصمة SHA-256 الكاملة وتنظيف اسم الملف.'
                        : 'Received files stream to .part storage and commit to MediaStore/SAF only after full SHA-256 verification and filename sanitization.'}
                    </p>
                  </div>
                </div>
              </div>
            </section>
          </div>
        )}

        {section === 'qa' && (
          <section className="space-y-5 max-w-4xl">
            <div>
              <h2 className="text-xl font-bold text-[#102B50]">{t.qaTitle}</h2>
              <p className="text-sm text-[#4B6280] mt-1">{t.qaSubtitle}</p>
            </div>
            <div className="border border-[#DCE6F4] rounded-2xl divide-y divide-[#F4F7FC] bg-[#FFFFFF]">
              {QA_ITEMS.map((item) => {
                const done = !!qaChecked[item.id];
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => toggleQa(item.id)}
                    className="w-full text-start px-5 py-4 flex items-center gap-3.5 hover:bg-[#F4F7FC] transition-colors cursor-pointer"
                  >
                    {done ? (
                      <CheckSquare className="w-5 h-5 text-[#1769D2] shrink-0" />
                    ) : (
                      <Square className="w-5 h-5 text-[#6B809B] shrink-0" />
                    )}
                    <span className={`text-sm font-medium ${done ? 'line-through text-[#6B809B]' : 'text-[#102B50]'}`}>
                      {lang === 'ar' ? item.ar : item.en}
                    </span>
                  </button>
                );
              })}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
