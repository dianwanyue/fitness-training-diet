import test from "node:test";
import assert from "node:assert/strict";
import {
  today,
  dateRange,
  nutrition,
  totals,
  goalAt,
  summarize,
} from "../src/lib/domain.js";

test("today uses Asia/Shanghai at the UTC day boundary", () => {
  const RealDate = globalThis.Date;
  try {
    globalThis.Date = class extends RealDate {
      constructor(...args) {
        super(...(args.length ? args : ["2026-10-20T18:00:00Z"]));
      }
    };
    assert.equal(today(), "2026-10-21");
  } finally {
    globalThis.Date = RealDate;
  }
});

test("dateRange is inclusive, ascending, leap-year and year-boundary safe", () => {
  assert.deepEqual(dateRange("2024-03-01", 3), [
    "2024-02-28",
    "2024-02-29",
    "2024-03-01",
  ]);
  assert.deepEqual(dateRange("2026-01-02", 3), [
    "2025-12-31",
    "2026-01-01",
    "2026-01-02",
  ]);
  assert.deepEqual(dateRange("2026-10-21", 0), []);
  assert.throws(() => dateRange("2026-02-29", 1), RangeError);
  assert.throws(() => dateRange("2026-10-21", -1), RangeError);
  assert.throws(() => dateRange("2026-10-21", 1.5), RangeError);
});

test("nutrition uses every 100 grams without display rounding", () => {
  const food = { kcal: 120, protein: 8, carbs: 3.3, fat: 2.2 };
  assert.deepEqual(nutrition(food, 150), {
    kcal: 180,
    protein: 12,
    carbs: 4.95,
    fat: 3.3,
  });
  assert.deepEqual(nutrition(food, 0), {
    kcal: 0,
    protein: 0,
    carbs: 0,
    fat: 0,
  });
  assert.throws(() => nutrition(food, -1), RangeError);
  assert.throws(() => nutrition(food, NaN), RangeError);
});

test("totals add actual snapshots, never reapply grams or read live food values", () => {
  const records = [
    {
      grams: 150,
      kcal: 999,
      snapshot: { kcal: 180, protein: 12, carbs: 5, fat: 3 },
    },
    { grams: 200, snapshot: { kcal: 120, protein: 8, carbs: 10, fat: 2 } },
  ];
  assert.deepEqual(totals(records), {
    kcal: 300,
    protein: 20,
    carbs: 15,
    fat: 5,
  });
  assert.deepEqual(totals([]), { kcal: 0, protein: 0, carbs: 0, fat: 0 });
  assert.deepEqual(totals([{ snapshot: { kcal: NaN } }]), totals([]));
});

test("goalAt selects the most recent effective goal without sorting/mutating input", () => {
  const goals = [
    { id: "new", effectiveDate: "2026-10-20" },
    { id: "old", effectiveDate: "2026-10-01" },
  ];
  assert.equal(goalAt(goals, "2026-10-19").id, "old");
  assert.equal(goalAt(goals, "2026-10-20").id, "new");
  assert.equal(goalAt(goals, "2026-09-30"), null);
  assert.equal(goalAt([], "2026-10-21"), null);
  assert.equal(goals[0].id, "new");
});

test("summary excludes cancelled plans, counts temporary workouts and keeps missing days null", () => {
  const data = {
    plans: [
      { date: "2026-10-18", status: "completed" },
      { date: "2026-10-19", status: "pending" },
      { date: "2026-10-20", status: "cancelled" },
      { date: "2026-10-17", status: "completed" },
    ],
    workouts: [
      { date: "2026-10-18", planId: "p1" },
      { date: "2026-10-19", planId: null },
      { date: "2026-10-17", planId: null },
    ],
    diet: [
      { date: "2026-10-18", snapshot: { kcal: 180 } },
      { date: "2026-10-18", snapshot: { kcal: 120 } },
      { date: "2026-10-20", snapshot: { kcal: 0 } },
      { date: "2026-10-17", snapshot: { kcal: 999 } },
    ],
    body: [
      { date: "2026-10-18", weight: 70 },
      { date: "2026-10-20", weight: 72 },
      { date: "2026-10-17", weight: 99 },
    ],
  };
  assert.deepEqual(summarize(data, "2026-10-18", "2026-10-20"), {
    dates: ["2026-10-18", "2026-10-19", "2026-10-20"],
    energy: [300, null, 0],
    weights: [70, null, 72],
    completed: 1,
    planned: 2,
    completion: 50,
    temporaryWorkouts: 1,
    weightAverage: 71,
    weightDays: 2,
    dietDays: 2,
  });
});

test("empty intervals and cancelled-only plans have null averages/completion, not zero", () => {
  const result = summarize(
    { plans: [{ date: "2026-10-21", status: "cancelled" }] },
    "2026-10-21",
    "2026-10-21",
  );
  assert.equal(result.completion, null);
  assert.equal(result.weightAverage, null);
  assert.equal(result.weightDays, 0);
  assert.equal(result.dietDays, 0);
  assert.deepEqual(result.energy, [null]);
  assert.deepEqual(result.weights, [null]);
  assert.throws(() => summarize({}, "2026-10-22", "2026-10-21"), RangeError);
});

test("invalid numeric records are not interpreted as real observed days", () => {
  const result = summarize(
    {
      body: [
        { date: "2026-10-21", weight: null },
        { date: "2026-10-20", weight: NaN },
      ],
      diet: [{ date: "2026-10-21", snapshot: { kcal: null } }],
    },
    "2026-10-20",
    "2026-10-21",
  );
  assert.equal(result.weightDays, 0);
  assert.equal(result.dietDays, 0);
});
