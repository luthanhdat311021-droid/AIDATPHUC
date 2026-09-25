import { parseDocumentContent } from './services/extractors/documentParser.js';

async function runTest() {
  const filePath = 'c:/Users/lutha/OneDrive/Desktop/AI học tập/backend/uploads/1789548530312-379605224-project_8.pdf';
  console.log("Testing parseDocumentContent on:", filePath);
  const result = await parseDocumentContent(filePath, 'project_8.pdf', 'application/pdf');
  console.log("\n================ RESULT ================");
  console.log(result);
  console.log("========================================");
  console.log("Total extracted chars:", result.length);
}

runTest();
