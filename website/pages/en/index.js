/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require("react");

const CompLibrary = require("../../core/CompLibrary.js");

const highlightBlock = require("highlight.js");

const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

const siteConfig = require(process.cwd() + "/siteConfig.js");

function imgUrl(img) {
  return siteConfig.baseUrl + "img/" + img;
}

function docUrl(doc, language) {
  return siteConfig.baseUrl + "docs/" + (language ? language + "/" : "") + doc;
}

function pageUrl(page, language) {
  return siteConfig.baseUrl + (language ? language + "/" : "") + page;
}

class Button extends React.Component {
  render() {
    return (
      <div className="pluginWrapper buttonWrapper">
        <a className="button" href={this.props.href} target={this.props.target}>
          {this.props.children}
        </a>
      </div>
    );
  }
}

Button.defaultProps = {
  target: "_self"
};

const SplashContainer = props => (
  <div className="homeContainer">
    <div className="homeSplashFade">
      <div className="wrapper homeWrapper">{props.children}</div>
    </div>
  </div>
);

const logoStyle = {
  marginBottom: '-20px',
  marginRight: '10px',
  width: '77px',
  height: '77px'
};

const ProjectTitle = _ => (
  <h2 className="projectTitle">
    <img src={imgUrl("scalac-profiling-logo.png")} alt="logo" style={logoStyle}></img>{siteConfig.title}
    <small>{siteConfig.tagline}</small>
  </h2>
);

const PromoSection = props => (
  <div className="section promoSection">
    <div className="promoRow">
      <div className="pluginRowBlock">{props.children}</div>
    </div>
  </div>
);

class HomeSplash extends React.Component {
  render() {
    let language = this.props.language || "";
    return (
      <div className="lightBackground">
        <SplashContainer>
          <div className="inner">
            <ProjectTitle />
            <PromoSection>
              <Button href={docUrl("user-guide/motivation.html", language)}>
                Get started
              </Button>
            </PromoSection>
          </div>
        </SplashContainer>
      </div>
    );
  }
}

const Features = props => {
  const features = [
    {
      title: "Speed up compilation",
      content:
        "Analyze your Scala 2 project and chase down compilation time bottlenecks with a focus on implicit searches and macro expansions.",
      image: imgUrl("speed-up-compilation.png"),
      imageAlign: "right"
    },
    {
      title: "Optimize your macros",
      content:
        "As a library author, improve the implementation of your macros to avoid excessive compilation time.",
      image: imgUrl("macro-impl.png"),
      imageAlign: "left"
    }
  ];

  return (
    <div
      className="productShowcaseSection paddingBottom"
      style={{ textAlign: "center" }}
    >
      {features.map(feature => (
        <Block key={feature.title}>{[feature]}</Block>
      ))}
    </div>
  );
};

const Block = props => (
  <Container
    padding={["bottom", "top"]}
    id={props.id}
    background={props.background}
  >
    <GridBlock align="left" contents={props.children} layout={props.layout} />
  </Container>
);

class Index extends React.Component {
  render() {
    let language = this.props.language || "";
    return (
      <div>
        <HomeSplash language={language} />
          <div className="mainContainer">
            <Features />
          </div>
      </div>
    );
  }
}

module.exports = Index;
