import { dragController } from "./dragController.js";

export const calendarView = {
  render(tasks, containerSelector) {
    const safeTasks = Array.isArray(tasks) ? tasks : [];
    const container = document.querySelector(containerSelector);
    if (!container) return;

    container.innerHTML = '<div class="day-view"></div>';
    const dayView = container.querySelector(".day-view");

    for (let h = 0; h < 24; h++) {
      const slot = document.createElement("div");
      slot.className = "hour-slot";
      slot.dataset.hour = h;
      slot.addEventListener("dragover", (e) => {
        e.preventDefault();
        slot.classList.add("drag-over");
      });
      slot.addEventListener("dragleave", () => {
        slot.classList.remove("drag-over");
      });
      slot.addEventListener("drop", async (e) => {
        e.preventDefault();
        slot.classList.remove("drag-over");
        const rect = slot.getBoundingClientRect();
        const offsetY = e.clientY - rect.top;
        const totalHeight = rect.height;
        let targetHour = Math.floor((offsetY / totalHeight) * 24);
        let minute = 0;
        targetHour = Math.max(0, Math.min(23, targetHour));
        const today = new Date();
        today.setHours(targetHour, minute, 0, 0);
        const startTime = today.toISOString();
        const endTime = new Date(
          today.getTime() + 60 * 60 * 1000,
        ).toISOString();

        // Suporte a hábito (drag custom) e tarefa (drag padrão)
        let habitId = e.dataTransfer.getData(
          "application/x-neurotask-habit-id",
        );
        let duration =
          parseInt(
            e.dataTransfer.getData(
              "application/x-neurotask-habit-duration-min",
            ),
            10,
          ) || 60;
        let draggedTaskId = window.draggedTaskId;
        if (!draggedTaskId && e.dataTransfer) {
          draggedTaskId = e.dataTransfer.getData("taskId");
        }
        if (habitId) {
          await import("../api/taskService.js").then(({ taskService }) =>
            taskService.createFromHabit(habitId, startTime, endTime),
          );
          window.dispatchEvent(new CustomEvent("task-updated"));
        } else if (draggedTaskId) {
          await import("../api/taskService.js").then(({ taskService }) =>
            taskService.moveTask(draggedTaskId, startTime, endTime),
          );
          window.dispatchEvent(new CustomEvent("task-updated"));
        }
      });
      const label = document.createElement("span");
      label.textContent = `${h.toString().padStart(2, "0")}:00`;
      label.style.marginRight = "8px";
      slot.appendChild(label);
      safeTasks
        .filter((t) => {
          if (!t.startTime) return false;
          const date = new Date(t.startTime);
          return date.getHours() === h;
        })
        .forEach((t) => {
          const tEl = document.createElement("div");
          tEl.className = "scheduled-task";
          tEl.textContent = t.title;
          tEl.draggable = true;
          tEl.dataset.taskId = t.id;
          tEl.addEventListener("dragstart", (e) => {
            window.draggedTaskId = t.id;
            e.dataTransfer.effectAllowed = "move";
            e.dataTransfer.setData("taskId", t.id);
          });
          slot.appendChild(tEl);
        });
      dayView.appendChild(slot);
    }
  },

  /**
   * Cria uma linha de horário (label + drop zone)
   */
  createTimeRow(hour, tasks) {
    const row = document.createElement("div");
    row.className = "time-row";
    row.style.flex = "1";
    row.style.minHeight = 0;

    // Label de horário (ex: "09:00")
    const label = document.createElement("div");
    label.className = "time-label";
    label.textContent = `${hour.toString().padStart(2, "0")}:00`;

    // Zona de drop
    const dropZone = document.createElement("div");
    dropZone.className = "drop-zone";
    dropZone.dataset.hour = hour;

    // Configura drag-and-drop
    dragController.makeDroppable(dropZone, hour);

    // Verifica se há tarefa agendada para este horário
    const scheduledTask = tasks.find((t) => {
      if (!t.startTime || t.status !== "SCHEDULED") return false;
      const taskHour = new Date(t.startTime).getHours();
      return taskHour === hour;
    });

    if (scheduledTask) {
      const taskCard = this.createScheduledTaskCard(scheduledTask);
      dropZone.appendChild(taskCard);
    }

    row.appendChild(label);
    row.appendChild(dropZone);
    return row;
  },

  /**
   * Cria card de tarefa agendada (visualização no calendário)
   */
  createScheduledTaskCard(task) {
    const card = document.createElement("div");
    card.className = "task-card";
    card.dataset.taskId = task.id;
    const header = document.createElement("div");
    header.className = "task-header";

    const titleSpan = document.createElement("span");
    // Reaproveita o mesmo layout do título da timeline (week/day) para garantir a bolinha visível
    titleSpan.className = "task-title calendar-task-title";
    titleSpan.appendChild(this.createPriorityDot(task.priority));
    titleSpan.appendChild(document.createTextNode(task.title));

    const badgeSpan = document.createElement("span");
    badgeSpan.className = `badge ${task.priority}`;
    badgeSpan.appendChild(this.createPriorityDot(task.priority));
    badgeSpan.appendChild(document.createTextNode(task.priority));

    header.appendChild(titleSpan);
    header.appendChild(badgeSpan);

    const desc = document.createElement("div");
    if (task.description) {
      desc.className = "task-desc";
      desc.textContent = task.description;
    }

    const timeInfo = document.createElement("div");
    timeInfo.style.fontSize = "0.7rem";
    timeInfo.style.color = "rgba(255,255,255,0.6)";
    timeInfo.style.marginTop = "5px";
    timeInfo.textContent = `${this.formatTime(
      task.startTime,
    )} - ${this.formatTime(task.endTime)}`;

    const deleteBtn = document.createElement("button");
    deleteBtn.className = "delete-btn";
    deleteBtn.dataset.taskId = task.id;
    Object.assign(deleteBtn.style, {
      position: "absolute",
      top: "8px",
      right: "8px",
      background: "rgba(239, 68, 68, 0.2)",
      color: "#ef4444",
      border: "none",
      borderRadius: "50%",
      width: "24px",
      height: "24px",
      cursor: "pointer",
      fontWeight: "bold",
      fontSize: "14px",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      zIndex: "10",
    });
    deleteBtn.textContent = "✕";

    const focusBtn = document.createElement("button");
    focusBtn.className = "focus-trigger-btn";
    focusBtn.title = "Modo foco / Deep Work";
    focusBtn.textContent = "⚡";
    focusBtn.dataset.taskId = task.id;

    focusBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      window.dispatchEvent(
        new CustomEvent("focus-mode-start", { detail: { taskId: task.id } }),
      );
    });

    card.appendChild(header);
    if (task.description) card.appendChild(desc);
    card.appendChild(timeInfo);
    card.appendChild(deleteBtn);
    card.appendChild(focusBtn);

    // Adiciona interações
    dragController.makeUnschedulable(card, task.id);
    dragController.makeCompletable(card, task.id);

    return card;
  },

  /**
   * Formata hora para exibição (HH:MM)
   */
  formatTime(isoString) {
    if (!isoString) return "";
    const date = new Date(isoString);
    return date.toLocaleTimeString("pt-BR", {
      hour: "2-digit",
      minute: "2-digit",
    });
  },

  getPriorityColor(priority) {
    switch (priority) {
      case "HIGH":
        return "#ea4335";
      case "MEDIUM":
        return "#fbbc04";
      case "LOW":
        return "#34a853";
      default:
        return "#9ca3af";
    }
  },

  createPriorityDot(priority) {
    const dot = document.createElement("span");
    dot.className = "priority-dot";
    dot.style.display = "inline-block";
    dot.style.width = "10px";
    dot.style.height = "10px";
    dot.style.borderRadius = "50%";
    dot.style.marginRight = "6px";
    dot.style.background = this.getPriorityColor(priority);
    dot.style.flexShrink = "0";
    dot.style.border = "1.5px solid rgba(255,255,255,0.85)";
    dot.style.boxShadow = "0 0 0 1px rgba(0,0,0,0.35)"; // subtle ring for contrast
    return dot;
  },
};
