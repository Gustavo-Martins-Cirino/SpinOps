/**
 * App.js - Versão mínima para Hard Reset
 * @version 7.9.0 - Focus Mode Final Master Polish (2026-01-24)
 * 
 * Responsável apenas por:
 * - Checar token no localStorage
 * - Abrir tela de login se não houver token
 * - Carregar tarefas e renderizar o calendário, se autenticado
 */

import { taskService } from "./api/taskService.js";
import { calendarView } from "./modules/calendarView.js";

 const ELDERLY_MODE_STORAGE_KEY = "elderlyMode";

 function syncElderlyModeToggleUI(isEnabled) {
   const toggleEl = document.getElementById("elderlyModeToggle");
   if (!toggleEl) return;
   toggleEl.classList.toggle("active", !!isEnabled);
   toggleEl.setAttribute("aria-checked", String(!!isEnabled));
 }

 function toggleElderlyMode(isEnabled) {
   document.body.classList.toggle("elderly-mode", !!isEnabled);
   syncElderlyModeToggleUI(!!isEnabled);
   try {
     localStorage.setItem(ELDERLY_MODE_STORAGE_KEY, String(!!isEnabled));
   } catch {
     // ignore storage failures
   }
 }

 function applyElderlyModeFromStorage() {
   let enabled = false;
   try {
     enabled = localStorage.getItem(ELDERLY_MODE_STORAGE_KEY) === "true";
   } catch {
     enabled = false;
   }
   document.body.classList.toggle("elderly-mode", enabled);
   syncElderlyModeToggleUI(enabled);
 }

// ==================== BOOTSTRAP SIMPLES ====================
document.addEventListener("DOMContentLoaded", async () => {
  applyElderlyModeFromStorage();

  let token = null;
  try {
    token = localStorage.getItem("neurotask_token");
  } catch {
    token = null;
  }

  // Sem token: mostra tela de login e não tenta carregar nada
  if (!token) {
    if (typeof window.showAuthView === "function") {
      window.showAuthView("login");
    }
    return;
  }

  // Com token: tenta carregar tarefas e renderizar o calendário
  try {
    initEventListeners();
    await refreshTasksAndCalendar();
  } catch (error) {
    alert("❌ Não foi possível carregar suas tarefas. Tente novamente.");
    console.error("Erro ao carregar tarefas:", error);
  }
});

 // Expor globalmente para uso em outras partes da UI (ex.: HTML inline)
 if (typeof window !== "undefined") {
   window.toggleElderlyMode = toggleElderlyMode;
 }

// ==================== RENDERIZAÇÃO BÁSICA ====================

async function refreshTasksAndCalendar() {
  const updatedTasks = await taskService.getAllTasks();
  if (typeof window !== "undefined") {
    window.tasks = updatedTasks;
  }
  if (typeof window.loadTasks === "function") {
    window.loadTasks();
  }
}

// Expor apenas refreshTasksAndCalendar globalmente
if (typeof window !== "undefined") {
  window.refreshTasksAndCalendar = refreshTasksAndCalendar;
}

// ==================== CRIAÇÃO DE TAREFAS VIA MODAL ====================

export async function addTaskFromModal() {
  // Coleta dados do modal de tarefa (IDs devem existir no index.html)
  const titleEl =
    document.getElementById("task-title") ||
    document.getElementById("modalTaskTitle");
  const descEl =
    document.getElementById("task-desc") ||
    document.getElementById("modalTaskDesc");

  // Prioridade: primeiro tenta um campo oculto padronizado, depois cai no selectedPriority global
  const priorityHidden = document.getElementById("task-priority");

  // Se os campos principais não existirem, retorna erro amigável
  if (!titleEl || !descEl) {
    alert(
      "❌ Não foi possível localizar o formulário de tarefa na tela. Atualize a página e tente novamente.",
    );
    return;
  }

  const title = titleEl.value?.trim();
  const description = descEl.value || "";
  let priority = "MEDIUM";
  if (priorityHidden && priorityHidden.value) {
    priority = priorityHidden.value;
  } else if (typeof window.selectedPriority === "string") {
    priority = window.selectedPriority;
  }

  // Coleta data e horários
  const dateEl = document.getElementById("modalTaskDate");
  const startTimeEl = document.getElementById("modalStartTime");
  const endTimeEl = document.getElementById("modalEndTime");

  let taskDate = dateEl?.value || "";
  if (!taskDate) {
    const today = new Date();
    taskDate = today.toISOString().split("T")[0]; // Formato YYYY-MM-DD
  }

  const startTime = startTimeEl?.value || "";
  const endTime = endTimeEl?.value || "";

  // Verificação de token ANTES de qualquer validação
  const token = localStorage.getItem("neurotask_token");
  if (!token) {
    alert("❌ Sessão expirada. Faça login novamente.");
    if (typeof window.showAuthView === "function") {
      window.showAuthView("login");
    }
    return;
  }

  if (!title) {
    alert("❌ Informe um título para a tarefa.");
    return;
  }

  console.log("Iniciando salvamento...");

  // Monta payload no mesmo formato que a IA usa
  const payload = {
    title,
    description,
    priority,
  };

  // Se tem horário, adiciona startTime/endTime no formato ISO completo
  // O backend define o status automaticamente (SCHEDULED se tiver horários, PENDING se não)
  if (startTime && endTime) {
    payload.startTime = `${taskDate}T${startTime}:00`;
    payload.endTime = `${taskDate}T${endTime}:00`;
  }

  console.log("Dados enviados:", payload);

  try {
    const createdTask = await taskService.createTask(payload);
    console.log("Tarefa salva com sucesso!", createdTask);

    // Atualiza grade/calendário imediatamente
    console.log("Iniciando atualização da grade...");

    try {
      await refreshTasksAndCalendar();
      console.info("✅ Tarefa atualizada na grade!");
    } catch (refreshError) {
      console.warn(
        "Erro no primeiro refresh, tentando novamente...",
        refreshError,
      );
      // Fallback: aguarda 500ms e tenta novamente
      await new Promise((resolve) => setTimeout(resolve, 500));
      await refreshTasksAndCalendar();
      console.info("✅ Grade atualizada após retry!");
    }

    // Limpa campos do modal APENAS após sucesso
    console.log("Limpando campos do modal...");
    if (titleEl) titleEl.value = "";
    if (descEl) descEl.value = "";
    if (dateEl) dateEl.value = "";
    if (startTimeEl) startTimeEl.value = "";
    if (endTimeEl) endTimeEl.value = "";

    // Fecha o modal automaticamente (método direto)
    console.log("Fechando modal...");
    const modalOverlay = document.getElementById("taskModalOverlay");
    if (modalOverlay) {
      modalOverlay.style.display = "none";
      modalOverlay.classList.remove("active");
      console.log("Modal fechado com sucesso!");
    } else {
      console.warn("taskModalOverlay não encontrado!");
    }
  } catch (error) {
    // Tenta extrair mensagem detalhada do servidor, se disponível
    let message =
      error && error.message ? error.message : "Erro ao salvar tarefa.";
    alert(`❌ ${message}`);
    console.error("Erro ao criar tarefa:", error);
  }
}

// Expor addTaskFromModal globalmente para uso em funções inline
if (typeof window !== "undefined") {
  window.addTaskFromModal = addTaskFromModal;
}

// ==================== CRIAÇÃO PROGRAMÁTICA DE TAREFAS (VIA IA OU JSON) ====================

/**
 * Cria uma tarefa programaticamente (ex.: via assistente IA)
 * sem depender do modal, garantindo envio do token.
 */
export async function createTaskProgrammatically(taskData) {
  const token = localStorage.getItem("neurotask_token");
  if (!token) {
    console.error("❌ Token ausente para criação via IA.");
    throw new Error("Sessão expirada. Faça login novamente.");
  }

  try {
    const task = await taskService.createTask(taskData);
    await refreshTasksAndCalendar();
    return task;
  } catch (error) {
    console.error("Erro ao criar tarefa via IA:", error);
    throw error;
  }
}

// Expor no escopo global para uso em scripts inline
if (typeof window !== "undefined") {
  window.createTaskProgrammatically = createTaskProgrammatically;
}

// ==================== LISTENERS RESILIENTES DE UI ====================

function initEventListeners() {
  // Botão SALVAR do modal de tarefa
  const saveTaskButton = document.getElementById("btn-save-task");
  if (saveTaskButton) {
    saveTaskButton.onclick = (e) => {
      e.preventDefault();
      addTaskFromModal();
    };
  }

  const elderlyModeToggle = document.getElementById("elderlyModeToggle");
  if (elderlyModeToggle) {
    elderlyModeToggle.addEventListener("click", () => {
      toggleElderlyMode(!document.body.classList.contains("elderly-mode"));
    });
  }

  // Botão "Nova Tarefa" da SIDEBAR / Dashboard
  const dashboardNewTaskButton = document.getElementById("dashboardNewTaskBtn");
  if (dashboardNewTaskButton) {
    dashboardNewTaskButton.addEventListener("click", (event) => {
      event.preventDefault();

      if (typeof window.showView === "function") {
        try {
          window.showView("tasks", event);
        } catch {
          // ignora falhas de navegação de view
        }
      }

      if (typeof window.openTaskModal === "function") {
        try {
          window.taskModalOrigin = "sidebar";
        } catch {
          // ignora se não existir
        }
        window.openTaskModal();
      }
    });
  }

  // Botão "Nova Tarefa" no DASHBOARD (caso exista no HTML)
  const dashboardQuickNewBtn = document.getElementById("dashboardNewTaskBtn2");
  if (dashboardQuickNewBtn) {
    dashboardQuickNewBtn.addEventListener("click", (event) => {
      event.preventDefault();

      if (typeof window.openTaskModal === "function") {
        try {
          window.taskModalOrigin = "dashboard";
        } catch {
          /* ignore */
        }
        window.openTaskModal();
      }
    });
  }

  // Botão "Nova Tarefa" no CALENDÁRIO (caso exista no HTML)
  const calendarNewTaskButton = document.getElementById("calendarNewTaskBtn");
  if (calendarNewTaskButton) {
    calendarNewTaskButton.addEventListener("click", (event) => {
      event.preventDefault();

      if (typeof window.openTaskModal === "function") {
        try {
          window.taskModalOrigin = "calendar";
        } catch {
          /* ignore */
        }
        window.openTaskModal();
      }
    });
  }
}

// ==================== MODO FOCO: THE PREMIUM EXPERIENCE (V5.1) ====================

// Estado Global do Foco
const focusState = {
  activeTaskId: null,
  timerInterval: null,
  timeLeft: 25 * 60,
  initialDuration: 25 * 60,
  isPaused: true,
  settings: {
    showSeconds: true,
    durationMinutes: 25,
    theme: "glass", // 'glass' | 'aura' | 'forest' | 'loft' | 'ocean' | 'solar' | 'cosmos' | 'concrete'
    soundEnabled: false,
  },
};

// Definição de Temas (V7.4 - Beige & Titanic Numbers)
const themes = {
  glass: {
    name: "Glassmorphism",
    bg: "rgba(30, 30, 30, 0.75)",
    backdrop: "blur(50px) saturate(1.5)",
    font: '"Montserrat", sans-serif',
    primary: "rgba(255, 255, 255, 0.95)",
    timerColor: "#ffffff",
    timerShadow: "0 0 40px rgba(0,0,0,0.5)",
    timerFont: '"Inter", sans-serif',
    timerWeight: "900"
  },
  forest: {
    name: "Enchanted Forest",
    bg: "#0f1c15",
    videoSrc: "/assets/videos/forest.mp4",
    filter: "brightness(0.9)", 
    font: '"Montserrat", sans-serif',
    primary: "#ffffff",
    timerColor: "#ffffff",
    timerShadow: "0 10px 50px rgba(0,0,0,0.9)", 
    timerFont: '"Montserrat", sans-serif',
    timerWeight: "700"
  },
  ocean: {
    name: "Deep Ocean",
    bg: "#001e26",
    videoSrc: "/assets/videos/ocean.mp4",
    filter: "brightness(0.8)",
    font: '"Inter", sans-serif',
    primary: "#afeeee",
    timerColor: "#afeeee",
    timerShadow: "0 5px 30px rgba(0,0,0,0.8)",
    timerFont: '"Inter", sans-serif',
    timerWeight: "900",
    specialEffect: "bubbles"
  },
  cosmos: {
    name: "Cosmos V2",
    bg: "#000",
    videoSrc: "/assets/videos/cosmos.mp4",
    filter: "brightness(0.8)",
    videoOpacity: "0.88",
    overlayDarkness: "rgba(0,0,0,0.55)",
    font: '"Space Mono", monospace',
    primary: "#ffffff",
    timerColor: "#ffffff",
    timerShadow: "0 0 30px rgba(120, 0, 255, 0.6)",
    timerFont: '"Space Mono", monospace',
    timerWeight: "400",
    specialEffect: "stars_parallax"
  },
  concrete: {
    name: "Industrial Work",
    bg: "#434343",
    videoSrc: "/assets/videos/work-office.mp4",
    font: '"Montserrat", sans-serif',
    primary: "#ffffff",
    timerColor: "#ffffff",
    timerShadow: "0 5px 20px #000",
    timerFont: '"Inter", sans-serif',
    timerWeight: "900"
  },
  aura: {
    name: "Aura Flow",
    bg: "linear-gradient(-45deg, #ee7752, #e73c7e, #23a6d5, #23d5ab)",
    bgSize: "400% 400%",
    animation: "gradientFlow 15s ease infinite",
    font: '"Montserrat", sans-serif',
    primary: "#ffffff",
    timerColor: "#ffffff",
    timerShadow: "0 5px 15px rgba(0,0,0,0.3)",
    timerFont: '"Inter", sans-serif',
    timerWeight: "900"
  },
  sand: {
    name: "Duna Suave",
    bg: "#E8E4C9", // Beige/Sand
    bgImage: "linear-gradient(to bottom, #E8E4C9 0%, #D3CDB0 100%)",
    font: '"Montserrat", sans-serif',
    primary: "#4A4036", // Dark Coffee Brown
    timerColor: "#4A4036",
    timerShadow: "none",
    timerFont: '"Inter", sans-serif',
    timerWeight: "900",
    lightMode: true
  }
};

// Listener global
document.addEventListener(
  "click",
  function (e) {
    const btn = e.target.closest(".btn-focus-mode");
    if (btn) {
      e.preventDefault();
      e.stopPropagation();
      const taskId = btn.getAttribute("data-task-id");
      startFocusMode(taskId);
    }
  },
  true,
);

// Inicia (ou restaura) o Modo Foco
function startFocusMode(id) {
  if (focusState.activeTaskId !== id) {
    focusState.activeTaskId = id;
    focusState.timeLeft = focusState.settings.durationMinutes * 60;
    focusState.initialDuration = focusState.timeLeft;
    focusState.isPaused = true;
    if (focusState.timerInterval) clearInterval(focusState.timerInterval);
    focusState.timerInterval = null;
  }

  // Force default theme if undefined to 'forest' (video proof)
  if (!focusState.settings.theme || !themes[focusState.settings.theme]) {
      focusState.settings.theme = 'forest'; 
  }

  renderFocusOverlay();
  if (!focusState.timerInterval) startGlobalTimer();
}

function startGlobalTimer() {
  if (focusState.timerInterval) clearInterval(focusState.timerInterval);
  focusState.timerInterval = setInterval(() => {
    if (!focusState.isPaused && focusState.timeLeft > 0) {
      focusState.timeLeft--;
      updateFocusDisplay();
      updatePIPDisplay();
    } else if (focusState.timeLeft === 0 && !focusState.isPaused) {
      focusState.isPaused = true;
      alert("Sessão de Foco concluída!");
      updateFocusDisplay();
    }
  }, 1000);
}

// Renderiza a interface principal (V7.3 - Fixed Video Paths & Huge Timer)
function renderFocusOverlay() {
  document.getElementById("focus-overlay")?.remove();
  document.getElementById("focus-pip")?.remove();

  // Dados da tarefa
  let taskTitle = "Tarefa sem título";
  if (window.tasks && Array.isArray(window.tasks)) {
    const t = window.tasks.find(
      (tk) => String(tk.id) === String(focusState.activeTaskId),
    );
    if (t && t.title && t.title.trim()) {
      taskTitle = t.title;
    }
  }

  const themeKey = focusState.settings.theme || 'forest';
  const theme = themes[themeKey] || themes['forest'];
  const isLight = theme.lightMode || false;

  // Settings Panel Colors
  const panelBg = isLight ? "rgba(255,255,255,0.9)" : "rgba(20, 20, 20, 0.9)";
  const panelText = isLight ? "#000" : "#fff";

  // Container principal
  const overlay = document.createElement("div");
  overlay.id = "focus-overlay";

  const styleTag = document.createElement("style");
  styleTag.id = "focus-style-injected";
  styleTag.innerHTML = `
    @import url('https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700;900&family=Inter:wght@100;900&family=Space+Mono:wght@400&family=Roboto:wght@100&display=swap');
    
    @keyframes gradientFlow { 0% { background-position: 0% 50%; } 50% { background-position: 100% 50%; } 100% { background-position: 0% 50%; } }
    @keyframes pulse { 0%, 100% { opacity: 0.6; } 50% { opacity: 0.3; } }
    
    /* V7.3 Settings Card Border */
    .theme-card-option {
       border: 2px solid rgba(255,255,255,0.2);
       box-shadow: 0 4px 6px rgba(0,0,0,0.3);
    }
    .theme-card-option:hover {
       border-color: #fff !important;
       transform: scale(1.05);
    }

    /* V7.5 Updated Settings Styles */
    #focus-settings-panel h4, #focus-settings-panel span, #focus-settings-panel div {
        font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif !important;
    }

    /* TOGGLE SWITCH V7.6 - COMPACT */
    .ios-toggle { position: relative; display: inline-block; width: 42px; height: 24px; }
    .ios-toggle input { opacity: 0; width: 0; height: 0; }
    .ios-slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: rgba(255,255,255,0.2); transition: .3s; border-radius: 24px; backdrop-filter: blur(4px); }
    .ios-slider:before { position: absolute; content: ""; height: 20px; width: 20px; left: 2px; bottom: 2px; background-color: white; transition: .3s; border-radius: 50%; box-shadow: 0 2px 4px rgba(0,0,0,0.2); }
    input:checked + .ios-slider { background-color: #10b981; }
    input:checked + .ios-slider:before { transform: translateX(18px); }
  `;
  document.head.appendChild(styleTag);

  // Apply Overlay Styles (Z-INDEX 99999 - ISOLAMENTO TOTAL)
  Object.assign(overlay.style, {
    position: "fixed", top: "0", left: "0", width: "100%", height: "100%",
    backgroundColor: "transparent", 
    zIndex: "99999", color: theme.primary, fontFamily: theme.font,
    opacity: "0", transition: "opacity 0.4s ease",
    display: "flex", flexDirection: "column", justifyContent: "space-between",
    alignItems: "center", overflow: "hidden"
  });

  // --- BACKGROUND ENGINE ---
  
  // 1. Fallback Image Layer (Z-Index -100 - BEM ATRÁS DO TIMER)
  const bgLayer = document.createElement("div");
  Object.assign(bgLayer.style, {
    position: "absolute", top: -20, left: -20, width: "110%", height: "110%",
    backgroundColor: theme.bg,
    backgroundImage: theme.bgImage || "none",
    backgroundSize: "cover", backgroundPosition: "center",
    filter: theme.filter || "none", 
    zIndex: "-100", 
    pointerEvents: "none"
  });
  if (theme.animation) bgLayer.style.animation = theme.animation;
  overlay.appendChild(bgLayer);
  
  // 2. Video Layer (Z-Index -4)
  if (theme.videoSrc) {
     const video = document.createElement("video");
     video.id = "focus-video-bg";
     // FORCE SILENCE PROTOCOLS
     video.muted = true;
     video.defaultMuted = true;
     video.volume = 0;
     video.setAttribute("muted", "");
     video.setAttribute("playsinline", "");
     
     video.autoplay = true;
     video.loop = true;
     video.removeAttribute("controls");
     video.playsInline = true;
     video.src = theme.videoSrc; 
     
     // Watchdog to silence video if browser tries to be smart
     const silenceDog = setInterval(() => {
        if(video.volume > 0) video.volume = 0;
        if(!video.muted) video.muted = true;
     }, 500);
     video.addEventListener('remove', () => clearInterval(silenceDog));

     const videoOpacity = theme.videoOpacity != null ? String(theme.videoOpacity) : (isLight ? "1" : "0.6");
     Object.assign(video.style, {
         position: "absolute", top: "0", left: "0", width: "100%", height: "100%",
         objectFit: "cover",
         zIndex: "-90",
       opacity: videoOpacity,
         pointerEvents: "none",
         display: "block" // Force block
     });

     // Force Play
     video.oncanplay = () => { video.play().catch(e => console.log("Auto-play failed", e)); };
     
     overlay.appendChild(video);
  }

  // 3. Dark Overlay (Z-Index -3)
  // Essential for timer visibility over bright videos - V7.7 Darker Opacity
  const overlayDarkness =
    theme.overlayDarkness != null
      ? String(theme.overlayDarkness)
      : isLight
        ? "rgba(255,255,255,0.3)"
        : "rgba(0,0,0,0.75)";
  const dimOverlay = document.createElement("div");
  Object.assign(dimOverlay.style, {
      position: "absolute", top: "0", left: "0", width: "100%", height: "100%",
      backgroundColor: overlayDarkness,
      zIndex: "-80",
      pointerEvents: "none"
  });
  overlay.appendChild(dimOverlay);

  // --- HTML Structure (V7.3) ---
  overlay.innerHTML += `
    <!-- HEADER -->
    <div class="focus-ui-element" style="position: absolute; top: 40px; right: 40px; z-index: 50;">
       <button id="focus-btn-close" style="background:transparent; border:none; color:${theme.primary}; font-size:30px; cursor:pointer; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.5));">✕</button>
    </div>

    <div class="focus-ui-element" style="position: absolute; top: 40px; left: 0; width: 100%; text-align: center; pointer-events: none; padding-top: 2vh; z-index: 10;">
       <div style="font-size: 14px; text-transform: uppercase; letter-spacing: 8px; opacity: 0.8; font-weight: 700; text-shadow: 0 2px 4px rgba(0,0,0,0.8);">FOCO ATIVO</div>
       <h1 style="font-size: 2rem; margin: 15px 0 0 0; font-weight: 700; text-shadow: 0 2px 10px rgba(0,0,0,0.8);">${taskTitle}</h1>
    </div>

    <!-- TIMER (V7.9 - BREAKOUT LAYOUT) -->
     <div id="focus-timer-container" style="
       position: fixed;
       top: 50%; left: 50%;
       transform: translate(-50%, -50%);
       display: flex !important; flex-direction: row !important; 
       justify-content: center; align-items: center;
       z-index: 5;
       width: 100vw;
       height: auto;
       text-align: center;
       font-variant-numeric: tabular-nums;
       pointer-events: none;
       will-change: transform;
     ">
       <!-- JS Injected here -->
    </div>

    <!-- CONTROLS -->
    <div class="focus-ui-element" style="position: absolute; bottom: 50px; left: 0; width: 100%; display: flex; flex-direction: column; align-items: center; gap: 20px; z-index: 50; pointer-events: none;">
        <button id="focus-btn-toggle" style="pointer-events: auto; background: rgba(255,255,255,0.1); border: 2px solid ${theme.primary}; color: ${theme.primary}; width: 80px; height: 80px; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s; backdrop-filter: blur(10px); margin-bottom: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.4);">
           <span id="icon-play" style="display:flex; align-items: center; justify-content: center; font-size: 32px;">▶</span>
        </button>
        <button id="focus-btn-complete" style="pointer-events: auto; padding: 14px 44px; border-radius: 50px; background: rgba(255,255,255,0.15); backdrop-filter: blur(10px); border: 1px solid rgba(255,255,255,0.3); color: ${theme.primary}; font-size: 15px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase; cursor: pointer; box-shadow: 0 4px 15px rgba(0,0,0,0.3);">
           Concluir Tarefa
        </button>
    </div>

    <!-- SETTINGS ICON -->
    <div class="focus-ui-element" style="position: absolute; bottom: 40px; right: 40px; z-index: 50;">
       <button id="focus-btn-settings" title="Ajustes" style="background: none; border: none; cursor: pointer; transition: transform 0.3s;">
          <svg width="36px" height="36px" viewBox="0 0 24 24" fill="#ffffff" style="filter: drop-shadow(0px 4px 6px rgba(0,0,0,0.8));">
             <path d="M19.14,12.94c0.04-0.3,0.06-0.61,0.06-0.94c0-0.32-0.02-0.64-0.06-0.94l2.03-1.58c0.18-0.14,0.23-0.41,0.12-0.61 l-1.92-3.32c-0.12-0.22-0.37-0.29-0.59-0.22l-2.39,0.96c-0.5-0.38-1.03-0.7-1.62-0.94L14.4,2.81c-0.04-0.24-0.24-0.41-0.48-0.41 h-3.84c-0.24,0-0.43,0.17-0.47,0.41L9.25,5.35C8.66,5.59,8.12,5.92,7.63,6.29L5.24,5.33c-0.22-0.08-0.47,0-0.59,0.22L2.73,8.87 C2.62,9.08,2.66,9.34,2.86,9.49l2.03,1.58C4.84,11.36,4.8,11.69,4.8,12s0.02,0.64,0.06,0.94l-2.03,1.58 c-0.18,0.14-0.23,0.41-0.12,0.61l1.92,3.32c0.12,0.22,0.37,0.29,0.59,0.22l2.39-0.96c0.5,0.38,1.03,0.7,1.62,0.94l0.36,2.54 c0.05,0.24,0.24,0.41,0.48,0.41h3.84c0.24,0,0.43-0.17,0.47-0.41l0.36-2.54c0.59-0.24,1.13-0.56,1.62-0.94l2.39,0.96 c0.22,0.08,0.47,0,0.59-0.22l1.92-3.32c0.12-0.22,0.07-0.47-0.12-0.61L19.14,12.94z M12,15.6c-1.98,0-3.6-1.62-3.6-3.6 s1.62-3.6,3.6-3.6s3.6,1.62,3.6,3.6S13.98,15.6,12,15.6z"/>
          </svg>
       </button>
    </div>

    <!-- SETTINGS PANEL -->
    <div id="focus-settings-panel" class="focus-ui-element" style="
       position: absolute; bottom: 100px; right: 40px; width: 340px;
       background: ${panelBg}; backdrop-filter: blur(40px);
       border-radius: 20px; padding: 25px; box-shadow: 0 20px 60px rgba(0,0,0,0.6); 
       color: ${panelText}; transform-origin: bottom right;
       transition: opacity 0.25s ease, transform 0.25s ease;
       opacity: 0; transform: scale(0.95) translateY(8px); pointer-events: none;
       z-index: 60; border: 1px solid rgba(255,255,255,0.1);
       overflow: hidden;
    ">
       <div style="display:flex; justify-content:space-between; margin-bottom: 20px; align-items:center;">
          <h4 style="margin:0; opacity: 0.8; font-weight: 800; font-size: 12px; letter-spacing: 2px;">AMBIENTE</h4>
          <button id="close-settings" style="background:none; border:none; font-size:22px; cursor:pointer; color:inherit; opacity:0.6;">✕</button>
       </div>
       
       <div style="margin-bottom: 30px;">
          <div style="display:grid; grid-template-columns: repeat(4, 1fr); gap: 10px;">
             ${[
               "forest",
               "ocean",
               "cosmos",
               "loft",
               "concrete",
               "glass",
               "sand",
               "aura"
             ]
               .map((t) => {
                 if (!themes[t]) return "";
                 const isActive = focusState.settings.theme === t;
                 return `
                <div class="theme-card-option" data-theme="${t}" title="${themes[t].name}" style="
                   height: 50px; border-radius: 12px; cursor: pointer;
                   background: ${themes[t].bg}; background-image: ${themes[t].bgImage || "none"}; background-size: cover;
                   background-position: center; 
                   border: 3px solid ${isActive ? '#fff' : 'rgba(255,255,255,0.2)'};
                   box-shadow: ${isActive ? '0 0 0 2px rgba(255,255,255,0.5)' : 'none'};
                   opacity: ${isActive ? "1" : "0.7"};
                   transition: border-color 0.15s, opacity 0.15s, box-shadow 0.15s;
                "></div>
             `;
               })
               .join("")}
          </div>
       </div>
       
       <div style="margin-bottom: 25px;">
          <div style="display:flex; justify-content:space-between; margin-bottom:10px; font-weight:700; font-size: 14px;">
             <span>Duração</span>
             <span id="val-duration" style="color:${theme.primary}">${focusState.settings.durationMinutes} min</span>
          </div>
          <input type="range" min="5" max="120" step="5" value="${focusState.settings.durationMinutes}" id="inp-duration" style="width:100%; accent-color: ${theme.primary}; height: 6px; border-radius: 5px;">
       </div>

       <div style="display:flex; justify-content: space-between; align-items: center; padding-top: 10px; border-top: 1px solid rgba(255,255,255,0.1);">
          <span style="font-weight: 700; font-size: 14px;">Mostrar Segundos</span>
          <label class="ios-toggle">
            <input type="checkbox" id="inp-seconds" ${focusState.settings.showSeconds ? "checked" : ""}>
            <span class="ios-slider"></span>
          </label>
       </div>
    </div>
  `;

  document.body.appendChild(overlay);
  overlay.getBoundingClientRect(); // Reflow
  overlay.style.opacity = "1";

  // --- Ghost UI Logic (auto-hide) ---
  // IMPORTANTE: #focus-timer-container NÃO tem classe .focus-ui-element, logo NUNCA será ocultado
  let idleTimeout = null;
  const resetIdleTimer = () => {
      overlay.style.cursor = 'default';
      const uiElements = overlay.querySelectorAll('.focus-ui-element');
      uiElements.forEach(el => {
          el.style.opacity = '1';
          el.style.pointerEvents = 'auto';
      });

      if(idleTimeout) clearTimeout(idleTimeout);

      if (!focusState.isPaused) {
          idleTimeout = setTimeout(() => {
              // Hide UI if playing (exceto timer, que não tem a classe .focus-ui-element)
              overlay.style.cursor = 'none'; // CURSOR GHOST
              const uiElements = overlay.querySelectorAll('.focus-ui-element');
              uiElements.forEach(el => {
                  if (el.id === 'focus-settings-panel' && el.style.opacity !== '0') return;
                  el.style.opacity = '0';
                  el.style.pointerEvents = 'none';
              });
          }, 3000);
      }
  };

  overlay.addEventListener('mousemove', resetIdleTimer);
  overlay.addEventListener('click', resetIdleTimer);

  const closeFn = () => {
    focusState.isPaused = true;
    // Limpar timer ao fechar
    if (focusState.timerInterval) {
      clearInterval(focusState.timerInterval);
      focusState.timerInterval = null;
    }
    overlay.style.opacity = "0";
    setTimeout(() => {
      overlay.remove();
      const s = document.getElementById("focus-style-injected");
      if (s) s.remove();
      document.body.style.cursor = 'default'; 
    }, 400);
  };

  overlay.querySelector("#focus-btn-close").onclick = closeFn;

  overlay.querySelector("#focus-btn-toggle").onclick = () => {
    focusState.isPaused = !focusState.isPaused;
    updateControlsUI();
    resetIdleTimer();
  };
  overlay.querySelector("#focus-btn-complete").onclick = async () => {
    if (confirm("Concluir esta tarefa agora?")) {
      await taskService.completeTask(focusState.activeTaskId);
      if (window.refreshTasksAndCalendar)
        await window.refreshTasksAndCalendar();
      closeFn();
    }
  };

  const panel = overlay.querySelector("#focus-settings-panel");
  
  // FIX 3: Estado explícito com variável global para evitar loops de evento
  let configOpen = false;
  
  const openPanel = () => {
    configOpen = true;
    panel.style.opacity = "1";
    panel.style.transform = "scale(1) translateY(0)";
    panel.style.pointerEvents = "all";
  };
  
  const closePanel = () => {
    configOpen = false;
    panel.style.opacity = "0";
    panel.style.transform = "scale(0.95) translateY(8px)";
    panel.style.pointerEvents = "none";
  };

  overlay.querySelector("#focus-btn-settings").onclick = (e) => {
    e.stopPropagation();
    e.preventDefault();
    // Estado explícito: se está aberto, fecha; se está fechado, abre
    if (configOpen) {
      closePanel();
    } else {
      openPanel();
    }
  };
  overlay.querySelector("#close-settings").onclick = (e) => {
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();
    closePanel();
    return false;
  };
  overlay.onclick = (e) => {
    resetIdleTimer();
    // Clique fora do painel E fora do botão de settings: fecha
    if (!panel.contains(e.target) && !e.target.closest("#focus-btn-settings") && !e.target.closest("#close-settings")) {
      closePanel();
    }
  };

  // Inputs
  overlay.querySelector("#inp-duration").oninput = (e) => {
    const v = parseInt(e.target.value);
    overlay.querySelector("#val-duration").innerText = v + " min";
    focusState.settings.durationMinutes = v;
    if (focusState.isPaused) {
      focusState.timeLeft = v * 60;
      updateFocusDisplay();
    }
  };
  overlay.querySelector("#inp-seconds").onchange = (e) => {
    focusState.settings.showSeconds = e.target.checked;
    updateFocusDisplay();
  };
  
  // THEME SWITCHER LOGIC
  overlay.querySelectorAll(".theme-card-option").forEach(
    (c) =>
      (c.onclick = () => {
        const newThemeName = c.dataset.theme;
        if (focusState.settings.theme === newThemeName) return;
        focusState.settings.theme = newThemeName;
        renderFocusOverlay();
      }),
  );

  updateControlsUI();
  updateFocusDisplay();
}

function updateControlsUI() {
  const icon = document.getElementById("icon-play");
  if (!icon) return;
  
  // ALINHAMENTO ÓPTICO DO PLAY
  if (focusState.isPaused) {
    icon.innerHTML = `<svg width="36" height="36" fill="currentColor" viewBox="0 0 24 24" style="transform: translateX(3px);"><path d="M8 5v14l11-7z"/></svg>`;
    console.log('▶️ Ícone PLAY ativo com alinhamento óptico (translateX: 3px)');
  } else {
    icon.innerHTML = `<svg width="36" height="36" fill="currentColor" viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`;
    console.log('⏸️ Ícone PAUSE ativo');
  }
}

function updateFocusDisplay() {
  console.log('🚀 RENDERIZANDO TIMER GIGANTE AGORA');
  
  // Remove qualquer timer antigo
  let container = document.getElementById("focus-timer-container");
  if (container) {
    console.log('⚠️ Timer existente removido');
    container.remove();
  }

  // Cria um div totalmente novo no body (FORÇA BRUTA)
  container = document.createElement("div");
  container.id = "focus-timer-container";
  console.log('✅ ID DO TIMER:', container.id);
  
  // Tema atual
  const themeKey = focusState.settings.theme || 'glass';
  const theme = themes[themeKey] || themes['glass'];
  const isGlass = themeKey === 'glass';
  const isLight = theme.lightMode || false;

  // Estilos do container principal (Z-INDEX 100000 - ACIMA DE TUDO)
  container.style.cssText = `
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    z-index: 100000;
    display: flex;
    gap: 20px;
    justify-content: center;
    align-items: center;
    pointer-events: none;
  `;

  const m = Math.floor(focusState.timeLeft / 60);
  const s = focusState.timeLeft % 60;
  const showSec = focusState.settings.showSeconds;
  
  const minStr = String(m).padStart(2, "0");
  const secStr = String(s).padStart(2, "0");
  
  // TAMANHO REFINADO: elegante mas respirável
  const fontSize = showSec ? "15vw" : "22vw";
  
  // ESTÉTICA ZEN-TECH: Cards ultra-foscos cinza-chumbo
  const cardBg = 'rgba(20, 20, 20, 0.85)';
  const cardBlur = 'blur(40px)';
  const textColor = '#E0E0E0'; // Off-White para menos cansaço ocular
  const dividerOpacity = '0.08'; // Linha super sutil

  // HTML: Cards estilo Flip Clock
  if (showSec) {
    // MODO COM SEGUNDOS: 3 cards (MM : SS)
    container.innerHTML = `
      <!-- CARD MINUTOS -->
      <div class="flip-card" style="
        background: ${cardBg};
        backdrop-filter: ${cardBlur};
        border-radius: 12px;
        padding: 20px 30px;
        box-shadow: 0 20px 50px rgba(0,0,0,0.5);
        position: relative;
        overflow: hidden;
      ">
        <!-- Linha central (dobra do flip) -->
        <div style="
          position: absolute;
          top: 50%;
          left: 0;
          right: 0;
          height: 1px;
          background: rgba(255,255,255,${dividerOpacity});
          z-index: 1;
        "></div>
        
        <!-- Número -->
        <div style="
          font-family: 'Inter', 'Montserrat', sans-serif;
          font-size: ${fontSize};
          font-weight: 900;
          color: ${textColor};
          line-height: 1;
          font-variant-numeric: tabular-nums;
          letter-spacing: -0.02em;
        ">${minStr}</div>
      </div>

      <!-- SEPARADOR : -->
      <div style="
        font-family: 'Inter', sans-serif;
        font-size: calc(${fontSize} * 0.7);
        font-weight: 300;
        color: ${textColor};
        opacity: 0.6;
        line-height: 1;
        animation: ${!focusState.isPaused ? 'pulse 2s infinite' : 'none'};
      ">:</div>

      <!-- CARD SEGUNDOS -->
      <div class="flip-card" style="
        background: ${cardBg};
        backdrop-filter: ${cardBlur};
        border-radius: 12px;
        padding: 20px 30px;
        box-shadow: 0 20px 50px rgba(0,0,0,0.5);
        position: relative;
        overflow: hidden;
      ">
        <!-- Linha central -->
        <div style="
          position: absolute;
          top: 50%;
          left: 0;
          right: 0;
          height: 1px;
          background: rgba(255,255,255,${dividerOpacity});
          z-index: 1;
        "></div>
        
        <!-- Número -->
        <div style="
          font-family: 'Inter', 'Montserrat', sans-serif;
          font-size: ${fontSize};
          font-weight: 900;
          color: ${textColor};
          line-height: 1;
          font-variant-numeric: tabular-nums;
          letter-spacing: -0.02em;
        ">${secStr}</div>
      </div>
    `;
  } else {
    // MODO SEM SEGUNDOS: 1 card grande (MM)
    container.innerHTML = `
      <div class="flip-card" style="
        background: ${cardBg};
        backdrop-filter: ${cardBlur};
        border-radius: 12px;
        padding: 30px 50px;
        box-shadow: 0 20px 50px rgba(0,0,0,0.5);
        position: relative;
        overflow: hidden;
      ">
        <!-- Linha central (dobra do flip) -->
        <div style="
          position: absolute;
          top: 50%;
          left: 0;
          right: 0;
          height: 1px;
          background: rgba(255,255,255,${dividerOpacity});
          z-index: 1;
        "></div>
        
        <!-- Número -->
        <div style="
          font-family: 'Inter', 'Montserrat', sans-serif;
          font-size: ${fontSize};
          font-weight: 900;
          color: ${textColor};
          line-height: 1;
          font-variant-numeric: tabular-nums;
          letter-spacing: -0.02em;
        ">${minStr}</div>
      </div>
    `;
  }

  // FORÇA BRUTA: Anexa ao overlay de foco (não ao body)
  const focusOverlay = document.getElementById('focus-overlay');
  if (focusOverlay) {
    focusOverlay.appendChild(container);
    console.log('✅ Flip Clock renderizado no OVERLAY - Tema:', themeKey, '| Tamanho:', fontSize, '| Z-Index: 100000');
  } else {
    // Fallback: anexa ao body se overlay não existir (não deveria acontecer)
    document.body.appendChild(container);
    console.warn('⚠️ Overlay não encontrado - Timer anexado ao body');
  }
}

// PIP Widget (Mini)
function renderPIP() {
  const theme = themes[focusState.settings.theme];
  // Simplificado para performance
  const pip = document.createElement("div");
  pip.id = "focus-pip";
  pip.className = "pip-active"; // Use CSS class if possible, but inline for now
  Object.assign(pip.style, {
    position: "fixed",
    bottom: "30px",
    right: "30px",
    background: theme.bg === "#e0ded9" ? "#fff" : "#1a1a1a",
    border: "1px solid rgba(255,255,255,0.1)",
    borderRadius: "16px",
    padding: "15px 25px",
    display: "flex",
    gap: "15px",
    alignItems: "center",
    boxShadow: "0 10px 40px rgba(0,0,0,0.3)",
    zIndex: "9999",
    cursor: "pointer",
    fontFamily: theme.font,
    color: theme.primary,
  });

  pip.innerHTML = `
     <div style="width:10px; height:10px; border-radius:50%; background:${focusState.isPaused ? "#f59e0b" : "#10b981"}; box-shadow: 0 0 10px currentColor;"></div>
     <span id="pip-time" style="font-size:20px; font-weight:700;">--:--</span>
  `;
  pip.onclick = () => {
    pip.remove();
    renderFocusOverlay();
  };
  document.body.appendChild(pip);
  updatePIPDisplay();
}

function updatePIPDisplay() {
  const el = document.getElementById("pip-time");
  if (el) {
    const m = Math.floor(focusState.timeLeft / 60);
    const s = focusState.timeLeft % 60;
    el.textContent = `${m}:${String(s).padStart(2, "0")}`;
  }
}

// Adjusted timer colon style
const timerColonStyle = {
  fontSize: "0.5em",
  opacity: 0.5,
};

// Apply static colon style
function updateTimerDisplay() {
  const timerElement = document.querySelector("#timer-display");
  if (timerElement) {
    const colon = timerElement.querySelector(".colon");
    if (colon) {
      Object.assign(colon.style, timerColonStyle);
    }
  }
}

// Ensure updateTimerDisplay is called on timer initialization
updateTimerDisplay();

// Expor globalmente
if (typeof window !== "undefined") {
  window.startFocusMode = startFocusMode;
}

// Global function to close settings modal
window.fecharConfiguracoes = (e) => {
  e.stopPropagation();
  const modal = document.getElementById('settingsView');
  if (modal) {
    modal.style.display = 'none';
  }
};
