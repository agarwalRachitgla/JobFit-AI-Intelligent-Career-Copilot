
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useState } from "react";
import "./App.css";

const API = "http://localhost:8080";

function App() {
  const [page, setPage] = useState("Dashboard");
  const [file, setFile] = useState(null);
  const [resumeText, setResumeText] = useState("");
  const [resumeResult, setResumeResult] = useState(null);
  const [jobDescription, setJobDescription] = useState("");
  const [matchResult, setMatchResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function uploadResume() {
    if (!file) {
      setError("Please select a PDF resume first.");
      return;
    }

    setLoading(true);
    setError("");
    setResumeResult(null);

    try {
      const formData = new FormData();
      formData.append("file", file);

      const response = await fetch(`${API}/api/resume/upload`, {
        method: "POST",
        body: formData,
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || "Resume upload failed.");
      }

      setResumeResult(data);
      setResumeText(data.extractedText || "");
      setMatchResult(null);
    } catch (err) {
      setError(err.message || "Could not connect to the backend.");
    } finally {
      setLoading(false);
    }
  }

  async function analyzeJob() {
    if (!resumeText.trim()) {
      setError("Upload your resume successfully before matching.");
      setPage("Resume Analysis");
      return;
    }

    if (!jobDescription.trim()) {
      setError("Please enter a job description.");
      return;
    }

    setLoading(true);
    setError("");
    setMatchResult(null);

    try {
      const response = await fetch(`${API}/api/job-match/analyze`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          resume: resumeText,
          jobDescription,
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || "Job analysis failed.");
      }

      setMatchResult(data);
      setPage("Job Matching");
    } catch (err) {
      setError(err.message || "Could not connect to the backend.");
    } finally {
      setLoading(false);
    }
  }

  const menu = [
    "Dashboard",
    "Resume Analysis",
    "Job Matching",
    "AI Career Coach",
  ];

  const recommendations = (matchResult?.improvementSuggestions || "")
    .replace(/\\#/g, "#")
    .replace(/\\\*/g, "*")
    .replace(/\\-/g, "-");

  return (
    <div className="app">
      <aside className="sidebar">
        <h2>JobFit AI</h2>
        <p>Intelligent Career Copilot</p>

        <nav>
          {menu.map((item) => (
            <button
              key={item}
              className={`nav-item ${page === item ? "active" : ""}`}
              onClick={() => {
                setPage(item);
                setError("");
              }}
            >
              {item}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">Powered by Local AI</div>
      </aside>

      <main className="main">
        <header className="topbar">
          <div>
            <p className="eyebrow">CAREER INTELLIGENCE PLATFORM</p>
            <h1>{page}</h1>
          </div>

          <div className="status">
            <span className="status-dot" />
            AI Workspace
          </div>
        </header>

        {error && <div className="error-message">{error}</div>}

        {/* DASHBOARD */}
        {page === "Dashboard" && (
          <>
            <section className="welcome">
              <p className="eyebrow">YOUR NEXT OPPORTUNITY STARTS HERE</p>
              <h2>Build your career with confidence.</h2>
              <p>
                Analyze your resume, discover skill gaps, and prepare
                for your next opportunity with AI.
              </p>

              <button onClick={() => setPage("Resume Analysis")}>
                Get Started →
              </button>
            </section>

            <section className="stats">
              {[
                ["01 / PROFILE", "Resume Analysis", "Upload and analyze your PDF resume."],
                ["02 / COMPATIBILITY", "Job Matching", "Compare your skills with a job."],
                ["03 / GROWTH", "AI Career Coach", "View personalized AI recommendations."],
              ].map(([label, title, description]) => (
                <button
                  className="stat-card"
                  key={title}
                  onClick={() => setPage(title)}
                >
                  <span>{label}</span>
                  <h3>{title}</h3>
                  <p>{description}</p>
                  <strong>Open feature →</strong>
                </button>
              ))}
            </section>
          </>
        )}

        {/* RESUME ANALYSIS */}
        {page === "Resume Analysis" && (
          <section className="workspace">
            <h2>Upload your resume</h2>
            <p>Select a PDF to extract your skills and generate AI analysis.</p>

            <label htmlFor="resumeFile">Resume PDF</label>

            <input
              id="resumeFile"
              type="file"
              accept=".pdf,application/pdf"
              onChange={(event) => {
                setFile(event.target.files?.[0] || null);
                setResumeResult(null);
                setError("");
              }}
            />

            {file && <p>Selected: {file.name}</p>}

            <button disabled={loading || !file} onClick={uploadResume}>
              {loading ? "Analyzing..." : "Upload and Analyze Resume"}
            </button>

            {resumeResult && (
              <div className="results">
                <h3>Resume Analysis Complete</h3>

                <p>
                  Technical skills: {resumeResult.technicalSkillCount ?? 0}
                </p>

                <p>Soft skills: {resumeResult.softSkillCount ?? 0}</p>

                <h3>Technical Skills</h3>
                <div className="chips">
                  {(resumeResult.technicalSkills || []).map((skill, index) => (
                    <span key={`${skill}-${index}`}>{skill}</span>
                  ))}
                </div>

                <h3>Soft Skills</h3>
                <div className="chips">
                  {(resumeResult.softSkills || []).map((skill, index) => (
                    <span key={`${skill}-${index}`}>{skill}</span>
                  ))}
                </div>

                <h3>AI Resume Feedback</h3>
                <pre>
                  {resumeResult.aiAnalysis || "No AI feedback returned."}
                </pre>

                <button onClick={() => setPage("Job Matching")}>
                  Match with a Job →
                </button>
              </div>
            )}
          </section>
        )}

        {/* JOB MATCHING */}
        {page === "Job Matching" && (
          <section className="workspace">
            <h2>Analyze a job opportunity</h2>

            <p>
              {resumeText
                ? "Resume uploaded. Add the job description below."
                : "Upload your resume in Resume Analysis before matching."}
            </p>

            {!resumeText && (
              <button onClick={() => setPage("Resume Analysis")}>
                Upload Resume →
              </button>
            )}

            <label htmlFor="jobDescription">Job description</label>

            <textarea
              id="jobDescription"
              value={jobDescription}
              onChange={(event) => setJobDescription(event.target.value)}
              placeholder="Paste the complete job description here..."
              rows={8}
            />

            <div className="form-footer">
              <span>{jobDescription.length} characters</span>

              <button
                disabled={loading || !resumeText || !jobDescription.trim()}
                onClick={analyzeJob}
              >
                {loading ? "Analyzing..." : "Analyze Job Match →"}
              </button>
            </div>

            {/* JOB MATCH RESULTS */}
            {matchResult && (
              <div className="results">
                <h2>Job Match Results</h2>

                <h3>
                  Matching Skills ({matchResult.matchingSkills?.length || 0})
                </h3>

                <div className="chips">
                  {(matchResult.matchingSkills || []).map((skill, index) => (
                    <span key={`${skill}-${index}`}>{skill}</span>
                  ))}
                </div>

                <h3>
                  Skills to Learn ({matchResult.missingSkills?.length || 0})
                </h3>

                <div className="chips missing">
                  {(matchResult.missingSkills || []).map((skill, index) => (
                    <span key={`${skill}-${index}`}>{skill}</span>
                  ))}
                </div>

                <h3>AI Career Recommendations</h3>

                <div className="recommendations-content">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {recommendations}
                  </ReactMarkdown>
                </div>
              </div>
            )}
          </section>
        )}

        {/* AI CAREER COACH */}
        {page === "AI Career Coach" && (
          <section className="workspace">
            <h2>Your AI Career Coach</h2>

            <p>
              {matchResult
                ? "Your latest job-specific recommendations are ready."
                : "Run a job match first to generate personalized recommendations."}
            </p>

            {matchResult ? (
              <div className="results">
                <div className="recommendations-content">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {recommendations}
                  </ReactMarkdown>
                </div>
              </div>
            ) : (
              <button onClick={() => setPage("Job Matching")}>
                Go to Job Matching →
              </button>
            )}
          </section>
        )}

        <footer>JobFit AI · Your skills. Your next move.</footer>
      </main>
    </div>
  );
}

export default App;
