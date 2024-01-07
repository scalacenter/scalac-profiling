// See https://docusaurus.io/docs/site-config.html for all the possible
// site configuration options.

const repoUrl = "https://github.com/scalacenter/scalac-profiling";

const siteConfig = {
  title: "scalac-profiling",
  tagline: "Compilation profiling tool for Scala 2 projects",

  url: "https://scalacenter.github.io/",
  baseUrl: "/scalac-profiling/",

  // Used for publishing and more
  projectName: "scalac-profiling",
  organizationName: "scalacenter",

  algolia: {
    apiKey: "",
    indexName: ""
  },

  // For no header links in the top nav bar -> headerLinks: [],
  headerLinks: [
    { doc: "user-guide/motivation", label: "Docs" },
    // TODO: Add the 'Case Studies' chapter
    // { doc: "case-studies/index", label: "Case Studies" },
    { href: repoUrl, label: "GitHub", external: true }
  ],

  // If you have users set above, you add it here:
  // users,

  /* path to images for header/footer */
  headerIcon: "img/scalac-profiling-logo.png",
  footerIcon: "img/scalac-profiling-logo.png",
  favicon: "img/favicon.png",

  /* colors for website */
  colors: {
    primaryColor: "#008040",
    secondaryColor: "#005028"
  },

  customDocsPath: "out",

  // This copyright info is used in /core/Footer.js and blog rss/atom feeds.
  copyright: `Copyright © ${new Date().getFullYear()} Scala Center`,

  highlight: {
    // Highlight.js theme to use for syntax highlighting in code blocks
    theme: "github"
  },

  /* On page navigation for the current documentation page */
  onPageNav: "separate",

  /* Open Graph and Twitter card images */
  ogImage: "img/scalac-profiling-logo.png",
  twitterImage: "img/scalac-profiling-logo.png",

  editUrl: `${repoUrl}/edit/main/docs/`,

  // Disabled because relative *.md links result in 404s.
  // cleanUrl: true,

  repoUrl
};

module.exports = siteConfig;
