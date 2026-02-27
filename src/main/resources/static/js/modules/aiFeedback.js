import { taskService } from "../api/taskService.js";

let productivityChart = null;

export const aiFeedback = {
  async renderDashboard() {
    try {
      const stats = await taskService.getStats();
      const priorityDist = await taskService.getPriorityDistribution();
      const dashboardSummary = await taskService.getDashboardSummary();

      this.updateStatsPanel(stats);

      this.updateCognitiveLoadBar(dashboardSummary);

      this.renderPriorityChart(priorityDist);
    } catch (error) {
      console.error("Erro ao carregar dashboard:", error);
    }
  },

  updateStatsPanel(stats) {
    const statsContainer = document.getElementById("stats-panel");
    if (!statsContainer) return;

    statsContainer.innerHTML = `
            <div class="stat-item">
                <div class="stat-value">${stats.total || 0}</div>
                <div class="stat-label">Total de Tarefas</div>
            </div>
            <div class="stat-item">
                <div class="stat-value">${stats.pending || 0}</div>
              <div class="stat-label">To Do (sem horário)</div>
            </div>
            <div class="stat-item">
                <div class="stat-value">${stats.scheduled || 0}</div>
                <div class="stat-label">Agendadas</div>
            </div>
            <div class="stat-item">
                <div class="stat-value">${stats.done || 0}</div>
                <div class="stat-label">Concluídas</div>
            </div>
            <div class="stat-item completion-rate">
                <div class="stat-value">${
                  stats.completionRate?.toFixed(0) || 0
                }%</div>
                <div class="stat-label">Taxa de Conclusão</div>
            </div>
        `;
  },

  /**
   * Atualiza barra de carga cognitiva (Energia Mental)
   */
  updateCognitiveLoadBar(summary) {
    if (!summary) return;

    const bar = document.getElementById("cognitive-bar-fill");
    const label = document.getElementById("cognitive-label");
    const valueEl = document.getElementById("cognitive-value");
    const panel = document.querySelector(".cognitive-panel");

    if (!bar || !label || !valueEl) return;

    const load = Math.max(0, Math.min(100, summary.cognitiveLoad || 0));
    valueEl.textContent = `${load}%`;
    bar.style.width = `${load}%`;

    // Reset estados visuais anteriores
    bar.classList.remove("alert-pulse");
    if (panel) {
      panel.classList.remove("alert-state");
    }

    let color = "#a3e635";
    let glow = "0 0 20px rgba(163, 230, 53, 0.55)";
    let text = "Mente Clara";

    if (load > 80) {
      color = "#ef4444";
      glow = "0 0 24px rgba(239, 68, 68, 0.75)";
      text = "ALERTA DE BURNOUT!";
      bar.classList.add("alert-pulse");
      if (panel) {
        panel.classList.add("alert-state");
      }
    } else if (load > 50) {
      color = "#f97316";
      glow = "0 0 22px rgba(249, 115, 22, 0.7)";
      text = "Foco Sob Pressão";
    }

    bar.style.background = color;
    bar.style.boxShadow = glow;
    label.textContent = text;
  },

  renderPriorityChart(distribution) {
    const canvas = document.getElementById("priority-chart");
    if (!canvas) return;

    if (productivityChart) {
      productivityChart.destroy();
    }

    const ctx = canvas.getContext("2d");
    productivityChart = new Chart(ctx, {
      type: "doughnut",
      data: {
        labels: ["Alta", "Média", "Baixa"],
        datasets: [
          {
            data: [
              distribution.HIGH || 0,
              distribution.MEDIUM || 0,
              distribution.LOW || 0,
            ],
            backgroundColor: [
              "rgba(239, 68, 68, 0.8)",
              "rgba(245, 158, 11, 0.8)",
              "rgba(16, 185, 129, 0.8)",
            ],
            borderColor: [
              "rgba(239, 68, 68, 1)",
              "rgba(245, 158, 11, 1)",
              "rgba(16, 185, 129, 1)",
            ],
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: "bottom",
            labels: {
              color: "#94a3b8",
              font: { size: 12 },
            },
          },
          title: {
            display: true,
            text: "Distribuição por Prioridade",
            color: "#f8fafc",
            font: { size: 14, weight: "bold" },
          },
        },
      },
    });
  },

  /**
   * Solicita análise de produtividade com IA
   */
  async getAIAnalysis(analysisType = "productivity") {
    try {
      const aiContainer = document.getElementById("ai-insights");
      if (!aiContainer) return;

      aiContainer.innerHTML =
        '<p style="text-align:center;">🤖 Analisando com IA...</p>';

      const analysis = await taskService.analyzeProductivity(analysisType);

      aiContainer.innerHTML = `
                <h3 style="font-size: 0.9rem; margin-bottom: 10px; color: var(--primary-accent);">
                    ${
                      analysis.score
                        ? `📊 Score: ${analysis.score}%`
                        : "📊 Análise"
                    }
                </h3>
                <p style="font-size: 0.85rem; margin-bottom: 12px; line-height: 1.5;">
                    ${analysis.summary}
                </p>
                ${
                  analysis.insights && analysis.insights.length > 0
                    ? `
                    <div style="margin-top: 10px;">
                        <strong style="font-size: 0.8rem;">💡 Insights:</strong>
                        <ul style="font-size: 0.8rem; margin: 5px 0 0 20px; line-height: 1.6;">
                            ${analysis.insights
                              .map((i) => `<li>${i}</li>`)
                              .join("")}
                        </ul>
                    </div>
                `
                    : ""
                }
                ${
                  analysis.recommendations &&
                  analysis.recommendations.length > 0
                    ? `
                    <div style="margin-top: 10px;">
                        <strong style="font-size: 0.8rem;">🎯 Recomendações:</strong>
                        <ul style="font-size: 0.8rem; margin: 5px 0 0 20px; line-height: 1.6;">
                            ${analysis.recommendations
                              .map((r) => `<li>${r}</li>`)
                              .join("")}
                        </ul>
                    </div>
                `
                    : ""
                }
            `;
    } catch (error) {
      const aiContainer = document.getElementById("ai-insights");
      if (aiContainer) {
        aiContainer.innerHTML = `<p style="color: var(--danger);">❌ Erro: ${error.message}</p>`;
      }
    }
  },
};
