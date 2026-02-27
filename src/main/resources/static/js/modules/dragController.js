import { taskService } from "../api/taskService.js";

let draggedTaskId = null;

let autoScrollRafId = null;
let autoScrollState = {
  container: null,
  clientY: null,
};

function stopAutoScroll() {
  if (autoScrollRafId != null) {
    cancelAnimationFrame(autoScrollRafId);
    autoScrollRafId = null;
  }
  autoScrollState = {
    container: null,
    clientY: null,
  };
}

function ensureAutoScroll(container, clientY) {
  if (!container || !Number.isFinite(clientY)) return;

  autoScrollState.container = container;
  autoScrollState.clientY = clientY;

  if (autoScrollRafId != null) return;

  const tick = () => {
    const scroller = autoScrollState.container;
    const y = autoScrollState.clientY;
    if (!scroller || !Number.isFinite(y)) {
      stopAutoScroll();
      return;
    }

    const rect = scroller.getBoundingClientRect();
    const threshold = 90;
    const maxScrollTop = scroller.scrollHeight - scroller.clientHeight;

    if (maxScrollTop > 0) {
      if (y < rect.top + threshold) {
        const dist = Math.max(0, rect.top + threshold - y);
        const step = Math.min(42, Math.max(10, dist * 0.22));
        scroller.scrollTop = Math.max(0, scroller.scrollTop - step);
      } else if (y > rect.bottom - threshold) {
        const dist = Math.max(0, y - (rect.bottom - threshold));
        const step = Math.min(42, Math.max(10, dist * 0.22));
        scroller.scrollTop = Math.min(maxScrollTop, scroller.scrollTop + step);
      }
    }

    autoScrollRafId = requestAnimationFrame(tick);
  };

  autoScrollRafId = requestAnimationFrame(tick);
}

function getClosestHourEl(el) {
  if (!el) return null;
  if (el.closest) {
    return el.closest("[data-hour]");
  }
  return null;
}

function getClosestTaskIdEl(el) {
  if (!el) return null;
  if (!el.closest) return null;
  return el.closest("[data-task-id], [data-taskid], [data-task-id]");
}

function getTaskIdFromEl(el) {
  if (!el) return null;
  const ds = el.dataset || {};
  return ds.taskId || ds.taskid || null;
}

export const dragController = {
  makeDraggable(taskElement, taskId) {
    taskElement.setAttribute("draggable", "true");
    taskElement.dataset.taskId = taskId;

    taskElement.addEventListener("dragstart", (e) => {
      draggedTaskId = taskId;
      taskElement.style.opacity = "0.4";
      taskElement.classList.add("dragging");
      e.dataTransfer.effectAllowed = "move";
    });

    taskElement.addEventListener("dragend", () => {
      taskElement.style.opacity = "1";
      taskElement.classList.remove("dragging");
      draggedTaskId = null;
      stopAutoScroll();
    });
  },

  makeFullDayDroppable(dayContainer, onDropTask) {
    dayContainer.addEventListener("dragover", (e) => {
      e.preventDefault();
      dayContainer.classList.add("drag-over");

      ensureAutoScroll(dayContainer, e.clientY);
    });

    dayContainer.addEventListener("dragleave", () => {
      dayContainer.classList.remove("drag-over");
      stopAutoScroll();
    });

    dayContainer.addEventListener("drop", async (e) => {
      e.preventDefault();
      dayContainer.classList.remove("drag-over");
      stopAutoScroll();

      const taskIdA =
        draggedTaskId ||
        (e.dataTransfer ? e.dataTransfer.getData("text/plain") : null);
      if (!taskIdA) {
        alert("Nenhuma tarefa foi arrastada.");
        return;
      }

      const dropTarget = document.elementFromPoint(e.clientX, e.clientY);

      const hourEl = getClosestHourEl(dropTarget);
      const hour = hourEl?.dataset?.hour;

      if (!dropTarget || hour == null) {
        alert("Não foi possível determinar o horário de destino.");
        return;
      }

      const taskEl = getClosestTaskIdEl(dropTarget);
      const taskIdB = getTaskIdFromEl(taskEl);

      const today = new Date().toISOString().split("T")[0];
      const startTime = `${today}T${hour.padStart(2, "0")}:00:00`;
      const endTime = `${today}T${(parseInt(hour) + 1).toString().padStart(2, "0")}:00:00`;

      try {
        document.body.style.cursor = "wait";

        if (taskIdB && String(taskIdB) !== String(taskIdA)) {
          const ok = confirm("Deseja trocar os horários entre as tarefas?");
          if (ok) {
            await taskService.swapTasks(taskIdA, taskIdB);
          } else {
            return;
          }
        } else {
          await taskService.updateTaskTime(taskIdA, startTime, endTime);
        }

        if (onDropTask) {
          onDropTask({ taskId: taskIdA, startTime, endTime });
        }
        window.dispatchEvent(new CustomEvent("task-updated"));
      } catch (error) {
        console.error("Erro ao mover tarefa:", error);
        alert(error?.message || "Conflito detectado. Não foi possível mover a tarefa.");
      } finally {
        document.body.style.cursor = "default";
      }
    });
  },

  makeUnschedulable(taskElement, taskId) {
    taskElement.addEventListener("dblclick", async () => {
      if (confirm("Remover horário desta tarefa?")) {
        try {
          await taskService.moveToBacklog(taskId);
          window.dispatchEvent(new CustomEvent("task-updated"));
        } catch (error) {
          alert(`Erro: ${error.message}`);
        }
      }
    });
  },

  makeCompletable(taskElement, taskId) {
    const btn = document.createElement("button");
    btn.innerHTML = "✓";
    btn.className = "complete-btn";
    btn.onclick = async (e) => {
      e.stopPropagation();
      try {
        await taskService.completeTask(taskId);
        taskElement.classList.add("task-completed");
        
        if (typeof triggerAchievementGlow === 'function') {
          triggerAchievementGlow(taskElement);
        }
        
        setTimeout(() => window.dispatchEvent(new CustomEvent("task-updated")), 300);
      } catch (error) {
        alert("Erro ao concluir tarefa.");
      }
    };
    taskElement.appendChild(btn);
  }
};