import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const datasetPath = path.join(root, "test-datasets", "full-coverage", "full-coverage-dataset.json");
const useCaseRoot = path.join(root, "documents", "use-cases");
const flowRoot = path.join(root, "documents", "flows");

function walk(dir, predicate, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, predicate, out);
    } else if (predicate(full)) {
      out.push(full);
    }
  }
  return out;
}

function rel(file) {
  return path.relative(root, file).replaceAll(path.sep, "/");
}

function firstHeading(file) {
  const text = fs.readFileSync(file, "utf8");
  return text
    .split(/\r?\n/)
    .map((line) => line.replace(/^\uFEFF/, ""))
    .find((line) => line.startsWith("# ")) ?? "";
}

function idFromHeading(heading) {
  const match = heading.match(/^#\s+(UC-[A-Z]+-\d{3})\b/);
  return match?.[1] ?? null;
}

const dataset = JSON.parse(fs.readFileSync(datasetPath, "utf8"));
const useCaseFiles = walk(useCaseRoot, (file) => file.endsWith(".md"));
const flowFiles = walk(flowRoot, (file) => file.endsWith(".md"));

const docsUseCases = new Map();
for (const file of useCaseFiles) {
  const id = idFromHeading(firstHeading(file));
  if (id) docsUseCases.set(id, rel(file));
}

const coveredUseCases = new Map();
for (const testCase of dataset.cases ?? []) {
  for (const id of testCase.coversUseCases ?? []) {
    if (!coveredUseCases.has(id)) coveredUseCases.set(id, []);
    coveredUseCases.get(id).push(testCase.id);
  }
}

const datasetFlowDocs = new Set(
  (dataset.businessFlowScenarios ?? []).map((flow) => flow.document)
);

const missingUseCases = [...docsUseCases.keys()].filter((id) => !coveredUseCases.has(id));
const staleUseCases = [...coveredUseCases.keys()].filter((id) => !docsUseCases.has(id));
const missingFlows = flowFiles.map(rel).filter((file) => !datasetFlowDocs.has(file));
const staleFlows = [...datasetFlowDocs].filter((file) => !fs.existsSync(path.join(root, file)));

console.log("Full coverage dataset audit");
console.log(`docs use cases      : ${docsUseCases.size}`);
console.log(`covered use cases   : ${coveredUseCases.size}`);
console.log(`docs business flows : ${flowFiles.length}`);
console.log(`covered flows       : ${datasetFlowDocs.size}`);
console.log(`dataset cases       : ${(dataset.cases ?? []).length}`);

function printList(title, rows) {
  if (!rows.length) return;
  console.log(`\n${title}`);
  for (const row of rows) console.log(`- ${row}`);
}

printList("Missing use cases", missingUseCases.map((id) => `${id} (${docsUseCases.get(id)})`));
printList("Stale use case ids", staleUseCases);
printList("Missing business flows", missingFlows);
printList("Stale business flow docs", staleFlows);

if (missingUseCases.length || staleUseCases.length || missingFlows.length || staleFlows.length) {
  process.exitCode = 1;
} else {
  console.log("\nRESULT: PASS");
}
