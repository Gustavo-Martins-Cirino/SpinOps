import { chromium } from "playwright";

const DEFAULT_BASE_URL = "http://localhost:8080";
const baseUrl = (process.env.BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, "");

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function yyyyMmDd(dateObj) {
  const yyyy = dateObj.getFullYear();
  const mm = String(dateObj.getMonth() + 1).padStart(2, "0");
  const dd = String(dateObj.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function toLocalDateTimeNoTZ(dateObj) {
  // Backend usa LocalDateTime; o frontend manda sem TZ.
  const yyyy = dateObj.getFullYear();
  const mm = String(dateObj.getMonth() + 1).padStart(2, "0");
  const dd = String(dateObj.getDate()).padStart(2, "0");
  const hh = String(dateObj.getHours()).padStart(2, "0");
  const mi = String(dateObj.getMinutes()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}T${hh}:${mi}:00`;
}

async function apiJson(path, options = {}) {
  const url = `${baseUrl}${path}`;
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      ...(options.headers || {}),
    },
  });

  const text = await response.text().catch(() => "");
  if (!response.ok) {
    throw new Error(
      `HTTP ${response.status} em ${path}: ${text || response.statusText}`
    );
  }
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function overlaps(aStart, aEnd, bStart, bEnd) {
  return !(aEnd <= bStart || aStart >= bEnd);
}

async function getScheduledTasksForDay(dateObj) {
  const all = (await apiJson("/api/tasks", { method: "GET" })) || [];
  const dayKey = yyyyMmDd(dateObj);
  return all
    .filter((t) => (t.status || "").toUpperCase() === "SCHEDULED")
    .filter((t) => t.startTime && t.endTime)
    .map((t) => ({
      ...t,
      _start: new Date(t.startTime),
      _end: new Date(t.endTime),
    }))
    .filter(
      (t) =>
        !Number.isNaN(t._start.getTime()) && !Number.isNaN(t._end.getTime())
    )
    .filter((t) => yyyyMmDd(t._start) === dayKey)
    .sort((a, b) => a._start - b._start);
}

async function findTwoFreeOneHourSlots(dateObj) {
  const busy = await getScheduledTasksForDay(dateObj);
  const intervals = busy.map((t) => {
    const startMin = t._start.getHours() * 60 + t._start.getMinutes();
    const endMin = t._end.getHours() * 60 + t._end.getMinutes();
    return { startMin, endMin };
  });

  const isSlotFree = (startMin, endMin) => {
    return !intervals.some((b) =>
      overlaps(startMin, endMin, b.startMin, b.endMin)
    );
  };

  const slots = [];
  // Evita madrugada para reduzir chance de edge-cases; ainda é agenda 24h.
  for (let h = 7; h <= 20; h += 1) {
    const startMin = h * 60;
    const endMin = startMin + 60;
    if (isSlotFree(startMin, endMin)) {
      slots.push({ hour: h, startMin, endMin });
      if (slots.length >= 2) break;
    }
  }

  if (slots.length < 2) {
    throw new Error(
      "Não encontrei 2 slots livres de 1h entre 07:00 e 20:00 para o teste de swap."
    );
  }

  return slots;
}

async function findTestDayWithTwoFreeSlots(startDateObj, maxDaysToTry = 21) {
  const base = new Date(startDateObj);
  base.setHours(0, 0, 0, 0);

  for (let i = 0; i < maxDaysToTry; i += 1) {
    const candidate = new Date(base);
    candidate.setDate(base.getDate() + i);
    try {
      const slots = await findTwoFreeOneHourSlots(candidate);
      return { day: candidate, slots };
    } catch {
      // tenta o próximo dia
    }
  }

  throw new Error(
    `Não encontrei 2 slots livres de 1h entre 07:00 e 20:00 em até ${maxDaysToTry} dias a partir de ${yyyyMmDd(
      base
    )} para o teste de swap.`
  );
}

async function main() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const consoleErrors = [];
  const pageErrors = [];
  const badResponses = [];

  page.on("console", (msg) => {
    if (msg.type() === "error") {
      consoleErrors.push(msg.text());
    }
  });
  page.on("pageerror", (err) => {
    pageErrors.push(String(err?.message || err));
  });

  page.on("response", (response) => {
    const status = response.status();
    if (status >= 400) {
      const url = response.url();
      // Evita poluir com coisas irrelevantes.
      if (!url.startsWith("data:")) {
        badResponses.push(`${status} ${url}`);
      }
    }
  });

  try {
    // Navegação inicial
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });

    // Espera a app carregar (evita correr antes de showView existir)
    await page.waitForFunction(
      () => typeof window.showView === "function",
      null,
      {
        timeout: 30_000,
      }
    );

    // =========================
    // Swap (Day view)
    // =========================
    // Usa uma data bem no futuro para evitar colisões com agenda real do usuário,
    // mas varre alguns dias caso esse dia já esteja "poluído" por execuções anteriores.
    const startTestDay = new Date();
    startTestDay.setFullYear(2099, 0, 7); // 07/Jan/2099
    startTestDay.setHours(0, 0, 0, 0);

    const { day: testDay, slots } = await findTestDayWithTwoFreeSlots(
      startTestDay,
      31
    );
    const [slotA, slotB] = slots;
    const runId = Date.now();
    const titleA = `SWAP_A_${runId}`;
    const titleB = `SWAP_B_${runId}`;

    const dateStr = yyyyMmDd(testDay);
    const startA = new Date(
      `${dateStr}T${String(slotA.hour).padStart(2, "0")}:00:00`
    );
    const endA = new Date(
      `${dateStr}T${String(slotA.hour + 1).padStart(2, "0")}:00:00`
    );
    const startB = new Date(
      `${dateStr}T${String(slotB.hour).padStart(2, "0")}:00:00`
    );
    const endB = new Date(
      `${dateStr}T${String(slotB.hour + 1).padStart(2, "0")}:00:00`
    );

    const createdA = await apiJson("/api/tasks", {
      method: "POST",
      body: JSON.stringify({
        title: titleA,
        description: "",
        priority: "MEDIUM",
        startTime: toLocalDateTimeNoTZ(startA),
        endTime: toLocalDateTimeNoTZ(endA),
        status: "SCHEDULED",
      }),
    });

    const createdB = await apiJson("/api/tasks", {
      method: "POST",
      body: JSON.stringify({
        title: titleB,
        description: "",
        priority: "MEDIUM",
        startTime: toLocalDateTimeNoTZ(startB),
        endTime: toLocalDateTimeNoTZ(endB),
        status: "SCHEDULED",
      }),
    });

    if (!createdA?.id || !createdB?.id) {
      throw new Error(
        "Falha ao criar tarefas do teste de swap (sem ID no retorno)."
      );
    }

    // Vai para Tasks + Day view, recarrega agenda
    await page.evaluate(async (dateStr) => {
      // Força o calendário/Hábitos para o mesmo dia do teste.
      // @ts-ignore
      if (typeof selectedDay !== "undefined")
        selectedDay = new Date(`${dateStr}T00:00:00`);

      // @ts-ignore
      showView("tasks");
      // @ts-ignore
      if (typeof changeCalendarView === "function") changeCalendarView("day");
      // @ts-ignore
      if (typeof loadTasks === "function") await loadTasks();
      // @ts-ignore
      if (typeof renderCalendar === "function") renderCalendar();
      // @ts-ignore
      if (typeof updateCalendarDateLabel === "function")
        updateCalendarDateLabel();
    }, dateStr);

    // Garante que os cards estão no DOM
    await page.waitForSelector(`text=${titleA}`, { timeout: 15_000 });
    await page.waitForSelector(`text=${titleB}`, { timeout: 15_000 });

    const cardA = page
      .locator(".agenda-event-card", { hasText: titleA })
      .first();
    await cardA.scrollIntoViewIfNeeded();

    // Alvo: slot da hora do B (drop em horário ocupado)
    const targetSlot = page
      .locator(".day-hour-row", {
        has: page.locator(".day-hour-label", {
          hasText: String(slotB.hour).padStart(2, "0"),
        }),
      })
      .locator(".day-hour-slot")
      .first();

    await targetSlot.scrollIntoViewIfNeeded();

    // Drag cardA -> slotB
    await cardA.dragTo(targetSlot);

    // Modal de confirmação deve aparecer
    await page.waitForSelector("#confirmOverlay.active", { timeout: 15_000 });
    const confirmOkBtn = page.locator("#confirmOkBtn");
    await confirmOkBtn.waitFor({ timeout: 15_000 });

    // Confirma swap
    await confirmOkBtn.click();

    // Aguarda recarga/UI atualizar
    await sleep(800);

    // Verifica via API (banco/contrato)
    const afterAll = (await apiJson("/api/tasks", { method: "GET" })) || [];
    const afterA = afterAll.find((t) => String(t.id) === String(createdA.id));
    const afterB = afterAll.find((t) => String(t.id) === String(createdB.id));

    if (!afterA?.startTime || !afterB?.startTime) {
      throw new Error(
        "Falha ao validar swap: tarefas não encontradas após swap."
      );
    }

    const afterAStart = new Date(afterA.startTime);
    const afterBStart = new Date(afterB.startTime);

    if (
      afterAStart.getHours() !== slotB.hour ||
      afterBStart.getHours() !== slotA.hour
    ) {
      throw new Error(
        `Swap não ocorreu como esperado. A=${afterAStart.toISOString()} (esperado hora ${
          slotB.hour
        }), ` + `B=${afterBStart.toISOString()} (esperado hora ${slotA.hour})`
      );
    }

    // Verifica na UI que A aparece na hora do B e B na hora do A
    await page.evaluate(async () => {
      // @ts-ignore
      if (typeof loadTasks === "function") await loadTasks();
      // @ts-ignore
      if (typeof renderCalendar === "function") renderCalendar();
    });

    await page.waitForSelector(`text=${titleA}`, { timeout: 15_000 });
    await page.waitForSelector(`text=${titleB}`, { timeout: 15_000 });

    const aRowOk = await page.evaluate(
      ({ title, hour }) => {
        const hourStr = String(hour).padStart(2, "0");
        const rows = Array.from(document.querySelectorAll(".day-hour-row"));
        const row = rows.find((r) => {
          const lbl = r.querySelector(".day-hour-label");
          return lbl && String(lbl.textContent || "").includes(hourStr);
        });
        if (!row) return false;
        return Array.from(row.querySelectorAll(".agenda-event-card")).some(
          (c) => String(c.textContent || "").includes(title)
        );
      },
      { title: titleA, hour: slotB.hour }
    );

    const bRowOk = await page.evaluate(
      ({ title, hour }) => {
        const hourStr = String(hour).padStart(2, "0");
        const rows = Array.from(document.querySelectorAll(".day-hour-row"));
        const row = rows.find((r) => {
          const lbl = r.querySelector(".day-hour-label");
          return lbl && String(lbl.textContent || "").includes(hourStr);
        });
        if (!row) return false;
        return Array.from(row.querySelectorAll(".agenda-event-card")).some(
          (c) => String(c.textContent || "").includes(title)
        );
      },
      { title: titleB, hour: slotA.hour }
    );

    if (!aRowOk || !bRowOk) {
      throw new Error(
        "Swap ocorreu no backend, mas a UI não refletiu as posições esperadas."
      );
    }

    // =========================
    // Hábitos: janelas livres e timeline 23:59
    // =========================
    await page.evaluate(() => {
      // @ts-ignore
      showView("habits");
    });

    await page.waitForSelector("#habitsClockSummary", { timeout: 15_000 });

    const habitsSummary = await page.locator("#habitsClockSummary").innerText();

    // Não pode ser o caso estático de dia inteiro (00:00–23:59) quando há tarefas.
    const normalized = habitsSummary.replace(/\s+/g, " ").trim();
    const hasFullDayWindow =
      normalized.includes("00h–23h59") ||
      normalized.includes("00h-23h59") ||
      normalized.includes("00:00–23:59") ||
      normalized.includes("00:00-23:59");

    if (hasFullDayWindow) {
      throw new Error(
        `Texto de 'Janelas livres' ainda parece estático (dia inteiro): "${habitsSummary}"`
      );
    }

    // Timeline deve permitir chegar até o final do dia.
    const timelineOk = await page.evaluate(() => {
      // O container é o próprio #habitsTimeline (ele já tem a classe .habits-timeline)
      const container = document.querySelector("#habitsTimeline");
      const track = document.querySelector(
        "#habitsTimeline .habits-timeline-track"
      );
      if (!container || !track) {
        return {
          ok: false,
          reason: "container/track ausente",
          overflowY: null,
          trackHeight: null,
        };
      }

      const overflowY = getComputedStyle(container).overflowY;
      const trackHeight = track.getBoundingClientRect().height;

      // Para 24h com ~0.833px/min => ~1200px. Usamos threshold conservador.
      const heightOk = Number.isFinite(trackHeight) && trackHeight > 900;
      const overflowOk = overflowY !== "hidden";
      return {
        ok: Boolean(heightOk && overflowOk),
        overflowY,
        trackHeight,
      };
    });

    if (!timelineOk.ok) {
      throw new Error(
        `Timeline de Hábitos não parece full-day/scrollável. reason=${
          timelineOk.reason ?? ""
        } overflowY=${timelineOk.overflowY} trackHeight=${
          timelineOk.trackHeight
        }`
      );
    }

    // =========================
    // Falhas de console/page
    // =========================
    if (pageErrors.length > 0) {
      throw new Error(`Erros de página: ${pageErrors.join(" | ")}`);
    }

    const isExpectedMove400 = (s) =>
      s.startsWith("400 ") && /\/api\/tasks\/\d+\/move\b/.test(s);

    const expectedMove400Seen = badResponses.some(isExpectedMove400);
    const filteredConsoleErrors = consoleErrors.filter((t) => {
      if (!expectedMove400Seen) return true;
      // Em alguns browsers, a tentativa de drop em slot ocupado gera este log genérico.
      return !(t.includes("Failed to load resource") && t.includes("400"));
    });

    if (filteredConsoleErrors.length > 0) {
      const uniqueBad = Array.from(new Set(badResponses))
        .filter((s) => !isExpectedMove400(s))
        .slice(0, 10);
      const badText =
        uniqueBad.length > 0 ? ` | HTTP>=400: ${uniqueBad.join(" ; ")}` : "";
      throw new Error(
        `Erros no console: ${filteredConsoleErrors.join(" | ")}${badText}`
      );
    }

    console.log("✅ Smoke (Fase 1) OK");
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch((err) => {
  console.error("❌ Smoke falhou:\n", err);
  process.exit(1);
});
