// All dates are business dates in Asia/Shanghai; calculations never use host time zone.
const NUTRIENTS = ["kcal", "protein", "carbs", "fat"];
const DAY = 86400000;

function dateValue(value) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new RangeError("日期必须为 YYYY-MM-DD");
  }
  const time = Date.parse(`${value}T00:00:00.000Z`);
  if (
    !Number.isFinite(time) ||
    new Date(time).toISOString().slice(0, 10) !== value
  ) {
    throw new RangeError("日期无效");
  }
  return time;
}

export function isBusinessDate(value) {
  try {
    dateValue(value);
    return true;
  } catch {
    return false;
  }
}

export function today() {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const part = (type) => parts.find((item) => item.type === type).value;
  return `${part("year")}-${part("month")}-${part("day")}`;
}

/** Ascending business dates, including end. */
export function dateRange(end, days) {
  const last = dateValue(end);
  if (!Number.isInteger(days) || days < 0)
    throw new RangeError("天数必须为非负整数");
  return Array.from({ length: days }, (_, index) =>
    new Date(last - (days - 1 - index) * DAY).toISOString().slice(0, 10),
  );
}

export function nutrition(food, grams) {
  const amount = Number(grams);
  if (!Number.isFinite(amount) || amount < 0)
    throw new RangeError("克重必须为非负数");
  return Object.fromEntries(
    NUTRIENTS.map((key) => {
      const value = Number(food?.[key] ?? 0);
      if (!Number.isFinite(value) || value < 0)
        throw new RangeError("营养值必须为非负数");
      return [key, (value * amount) / 100];
    }),
  );
}

/** Snapshots are actual consumed amounts, NOT per-100g food values. */
export function totals(records = []) {
  const result = { kcal: 0, protein: 0, carbs: 0, fat: 0 };
  for (const record of records) {
    for (const key of NUTRIENTS) {
      const value = record?.snapshot?.[key];
      if (typeof value === "number" && Number.isFinite(value))
        result[key] += value;
    }
  }
  return result;
}

export function goalAt(goals = [], date) {
  dateValue(date);
  return goals.reduce(
    (selected, goal) =>
      goal.effectiveDate <= date &&
      (!selected || goal.effectiveDate >= selected.effectiveDate)
        ? goal
        : selected,
    null,
  );
}

/** Missing days stay null; valid zeros remain zeros. No display rounding here. */
export function summarize(data, start, end) {
  const first = dateValue(start);
  const last = dateValue(end);
  if (first > last) throw new RangeError("开始日期不能晚于结束日期");
  const dates = dateRange(end, (last - first) / DAY + 1);
  const within = (record) => record.date >= start && record.date <= end;
  const dietByDate = new Map();
  for (const record of data?.diet ?? []) {
    const kcal = record?.snapshot?.kcal;
    if (
      within(record) &&
      typeof kcal === "number" &&
      Number.isFinite(kcal) &&
      kcal >= 0
    ) {
      dietByDate.set(record.date, (dietByDate.get(record.date) ?? 0) + kcal);
    }
  }
  const weightByDate = new Map();
  for (const record of data?.body ?? []) {
    if (
      within(record) &&
      typeof record.weight === "number" &&
      Number.isFinite(record.weight) &&
      record.weight > 0
    ) {
      weightByDate.set(record.date, record.weight);
    }
  }
  const plans = (data?.plans ?? []).filter(
    (plan) => within(plan) && plan.status !== "cancelled",
  );
  const completed = plans.filter((plan) => plan.status === "completed").length;
  const weights = dates.map((date) => weightByDate.get(date) ?? null);
  const validWeights = weights.filter((weight) => weight !== null);
  return {
    dates,
    energy: dates.map((date) => dietByDate.get(date) ?? null),
    weights,
    completed,
    planned: plans.length,
    completion: plans.length ? (completed / plans.length) * 100 : null,
    temporaryWorkouts: (data?.workouts ?? []).filter(
      (workout) => within(workout) && workout.planId == null,
    ).length,
    weightAverage: validWeights.length
      ? validWeights.reduce((sum, value) => sum + value, 0) /
        validWeights.length
      : null,
    weightDays: validWeights.length,
    dietDays: dates.filter((date) => dietByDate.has(date)).length,
  };
}
