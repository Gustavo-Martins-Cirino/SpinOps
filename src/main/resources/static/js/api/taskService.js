/**
 * Task Service - Camada de comunicação com a API REST
 * Encapsula todas as chamadas HTTP usando Fetch API nativa
 */

const API_BASE = "/api";

function getAuthToken() {
  try {
    return localStorage.getItem("neurotask_token");
  } catch (e) {
    return null;
  }
}

function handleAuthFailure(status) {
  if (status === 401) {
    try {
      localStorage.removeItem("neurotask_token");
    } catch {}
    try {
      if (
        typeof window !== "undefined" &&
        typeof window.showAuthView === "function"
      ) {
        window.showAuthView("login");
      }
    } catch {
      // ignore UI fallback errors
    }
    alert("Sessão expirada. Faça login novamente.");
  }
}

function getAuthHeaders(extra = {}) {
  const token = getAuthToken();
  if (token) {
    return { "X-Auth-Token": token, ...extra };
  }
  return { ...extra };
}

async function fetchWithRetry(url, options, retries = 1) {
  let attempts = 0;
  while (true) {
    try {
      attempts += 1;
      const response = await fetch(url, options);

      // Retenta apenas erros de servidor (5xx). 4xx e 401 são tratados fora.
      if (!response.ok && response.status >= 500 && retries > 0) {
        retries -= 1;
        await new Promise((resolve) => setTimeout(resolve, 1000));
        continue;
      }

      return response;
    } catch (error) {
      if (retries <= 0) {
        throw error;
      }
      retries -= 1;
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  }
}

export const taskService = {
  /**
   * GET /api/tasks - Lista todas as tarefas
   */
  async getAllTasks() {
    const response = await fetchWithRetry(`${API_BASE}/tasks`, {
      // Evita cache do navegador para garantir lista fresca após exclusão
      cache: "no-store",
      headers: getAuthHeaders({
        "Cache-Control": "no-cache",
        Pragma: "no-cache",
      }),
    });

    if (!response.ok) {
      handleAuthFailure(response.status);
      const error = new Error("Erro ao buscar tarefas");
      error.status = response.status;
      throw error;
    }
    return response.json();
  },

  async updateTaskTime(taskId, newStartTime, newEndTime) {
    return this.moveTask(taskId, newStartTime, newEndTime);
  },

  async swapTasks(taskId, otherTaskId) {
    const response = await fetchWithRetry(`${API_BASE}/tasks/${taskId}/swap`, {
      method: "PATCH",
      headers: getAuthHeaders({
        "Content-Type": "application/json; charset=utf-8",
      }),
      body: JSON.stringify({ otherTaskId }),
    });

    if (!response.ok) {
      handleAuthFailure(response.status);
      const error = await response.text();
      throw new Error(error || "Erro ao trocar horários");
    }
    return response.json();
  },

  /**
   * POST /api/tasks - Cria nova tarefa no backlog
   */
  async createTask(taskData) {
    const response = await fetchWithRetry(`${API_BASE}/tasks`, {
      method: "POST",
      headers: getAuthHeaders({
        "Content-Type": "application/json; charset=utf-8",
      }),
      body: JSON.stringify(taskData),
    });
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro ao criar tarefa");
    }
    return response.json();
  },

  // Fila para múltiplos hábitos (evita atropelar backend)
  _habitQueue: [],
  _habitProcessing: false,
  async enqueueHabitTask(payload) {
    this._habitQueue.push(payload);
    if (!this._habitProcessing) {
      this._processHabitQueue();
    }
  },
  async _processHabitQueue() {
    if (this._habitProcessing || this._habitQueue.length === 0) return;
    this._habitProcessing = true;
    while (this._habitQueue.length > 0) {
      const payload = this._habitQueue.shift();
      try {
        await this.createTask(payload);
      } catch (e) {
        console.error("Erro ao criar tarefa de hábito:", e);
      }
      // Pequeno delay entre requisições para não sobrecarregar
      await new Promise((r) => setTimeout(r, 300));
    }
    this._habitProcessing = false;
  },

  /**
   * PATCH /api/tasks/{id}/move - Move tarefa para o calendário (time-blocking)
   */
  async moveTask(taskId, newStartTime, newEndTime) {
    const response = await fetchWithRetry(`${API_BASE}/tasks/${taskId}/move`, {
      method: "PATCH",
      headers: getAuthHeaders({
        "Content-Type": "application/json; charset=utf-8",
      }),
      body: JSON.stringify({ newStartTime, newEndTime }),
    });

    if (!response.ok) {
      handleAuthFailure(response.status);
      const error = await response.text();
      throw new Error(error || "Erro ao mover tarefa");
    }
    return response.json();
  },

  /**
   * PATCH /api/tasks/{id}/complete - Marca tarefa como concluída
   */
  async completeTask(taskId) {
    const response = await fetchWithRetry(
      `${API_BASE}/tasks/${taskId}/complete`,
      {
        method: "PATCH",
        headers: getAuthHeaders(),
      },
    );
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro ao completar tarefa");
    }
    return response.json();
  },

  /**
   * PATCH /api/tasks/{id}/backlog - Volta tarefa para o backlog
   */
  async moveToBacklog(taskId) {
    const response = await fetchWithRetry(
      `${API_BASE}/tasks/${taskId}/backlog`,
      {
        method: "PATCH",
        headers: getAuthHeaders(),
      },
    );
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro ao mover para To Do (sem horário)");
    }
    return response.json();
  },

  /**
   * DELETE /api/tasks/{id} - Remove tarefa
   */
  async deleteTask(taskId) {
    const response = await fetchWithRetry(`${API_BASE}/tasks/${taskId}`, {
      method: "DELETE",
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro ao deletar tarefa");
    }
  },

  /**
   * GET /api/analytics/stats - Busca estatísticas gerais
   */
  async getStats() {
    const response = await fetchWithRetry(`${API_BASE}/analytics/stats`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro ao buscar estatísticas");
    }
    return response.json();
  },

  /**
   * GET /api/analytics/priority - Distribuição por prioridade
   */
  async getPriorityDistribution() {
    const response = await fetchWithRetry(`${API_BASE}/analytics/priority`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro ao buscar distribuição");
    }
    return response.json();
  },

  /**
   * POST /api/ai/analyze - Análise de produtividade com IA
   */
  async analyzeProductivity(
    analysisType = "productivity",
    timeRange = "today",
  ) {
    const response = await fetchWithRetry(`${API_BASE}/ai/analyze`, {
      method: "POST",
      headers: getAuthHeaders({
        "Content-Type": "application/json; charset=utf-8",
      }),
      body: JSON.stringify({ analysisType, timeRange }),
    });
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro na análise de IA");
    }
    return response.json();
  },

  /**
   * POST /api/ai/chat - Chat com IA
   */
  async chat(message) {
    const response = await fetchWithRetry(`${API_BASE}/ai/chat`, {
      method: "POST",
      headers: getAuthHeaders({
        "Content-Type": "application/json; charset=utf-8",
      }),
      body: JSON.stringify({ message }),
    });
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro no chat");
    }
    return response.json();
  },

  /**
   * GET /api/tasks/dashboard - Resumo do dashboard (carga cognitiva)
   */
  async getDashboardSummary() {
    const token = getAuthToken();
    if (!token) {
      alert("Faça login para visualizar seu dashboard personalizado.");
      throw new Error("Usuário não autenticado");
    }

    const response = await fetchWithRetry(`${API_BASE}/tasks/dashboard`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      handleAuthFailure(response.status);
      throw new Error("Erro ao buscar resumo de carga cognitiva");
    }
    return response.json();
  },

  /**
   * GET /api/analytics/status - Distribuição de OS por status
   */
  async getStatusDistribution() {
    const response = await fetchWithRetry(`${API_BASE}/analytics/status`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) throw new Error("Erro ao buscar distribuição de status");
    return response.json();
  },

  /**
   * GET /api/analytics/hourly - Distribuição de OS por hora do dia (0-23)
   */
  async getHourlyDistribution() {
    const response = await fetchWithRetry(`${API_BASE}/analytics/hourly`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) throw new Error("Erro ao buscar distribuição por hora");
    return response.json();
  },

  /**
   * GET /api/analytics/tags - Distribuição de OS por tag/categoria
   */
  async getTagDistribution() {
    const response = await fetchWithRetry(`${API_BASE}/analytics/tags`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) throw new Error("Erro ao buscar distribuição por tag");
    return response.json();
  },

  /**
   * GET /api/analytics/trend?days=N - Série temporal diária (criadas vs concluídas)
   */
  async getDailyTrend(days = 14) {
    const response = await fetchWithRetry(`${API_BASE}/analytics/trend?days=${days}`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) throw new Error("Erro ao buscar tendência diária");
    return response.json();
  },

  /**
   * GET /api/analytics/forecast - Previsão de produtividade por regressão linear
   * @param {number} historicDays - Dias históricos para o modelo (padrão: 14)
   * @param {number} horizon - Dias à frente a projetar (padrão: 7)
   */
  async getForecast(historicDays = 14, horizon = 7) {
    const response = await fetchWithRetry(
      `${API_BASE}/analytics/forecast?historicDays=${historicDays}&horizon=${horizon}`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) throw new Error("Erro ao buscar previsão de produtividade");
    return response.json();
  },

  /**
   * GET /api/analytics/anomalies - Detecção de anomalias de produtividade
   * @param {number} days - Janela de análise em dias (padrão: 30)
   */
  async getAnomalies(days = 30) {
    const response = await fetchWithRetry(
      `${API_BASE}/analytics/anomalies?days=${days}`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) throw new Error("Erro ao buscar anomalias");
    return response.json();
  },

  /**
   * GET /api/analytics/correlation - Correlações operacionais
   */
  async getCorrelation() {
    const response = await fetchWithRetry(
      `${API_BASE}/analytics/correlation`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) throw new Error("Erro ao buscar correlações");
    return response.json();
  },

  /**
   * GET /api/analytics/insights - Resumo executivo gerado por IA
   * Retorna: { narrative: "texto" }
   */
  async getInsights() {
    const response = await fetchWithRetry(
      `${API_BASE}/analytics/insights`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) throw new Error("Erro ao buscar insights");
    return response.json();
  },

  /**
   * GET /api/analytics/classify - Classificação automática de OS por categoria técnica
   * Retorna: [{ taskId, title, category, confidence }, ...]
   */
  async classifyTasks() {
    const response = await fetchWithRetry(
      `${API_BASE}/analytics/classify`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) throw new Error("Erro ao classificar OS");
    return response.json();
  },

  /**
   * GET /api/analytics/recommendations - Recomendações estruturadas de otimização via IA
   * Retorna: [{ action, impact, priority, category }, ...]
   */
  async getRecommendations() {
    const response = await fetchWithRetry(
      `${API_BASE}/analytics/recommendations`,
      { headers: getAuthHeaders() }
    );
    if (!response.ok) throw new Error("Erro ao buscar recomendações");
    return response.json();
  },
};

// Expõe globalmente para scripts inline que não são módulos ES
window.taskService = taskService;
