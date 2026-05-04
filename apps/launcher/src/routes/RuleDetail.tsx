import { Navigate, useNavigate, useParams } from "react-router";
import { ContentDetail } from "../components/content-pages/ContentDetail";
import { findRulesCategory } from "../lib/content-data";

export function RuleDetail() {
  const { slug = "" } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const cat = findRulesCategory(slug);

  if (!cat) {
    return <Navigate to="/rules" replace state={{ level: 2 }} />;
  }

  const intro = `Cette section couvre toutes les règles spécifiques à <b>${cat.name}</b>. Lis-les attentivement avant de soumettre ta demande de whitelist — le quiz d'accès tire ses questions directement de ces sections.`;

  return (
    <ContentDetail
      variant="rules"
      category={cat}
      intro={intro}
      sections={cat.sections}
      onBack={() => navigate("/rules", { state: { level: 2 } })}
    />
  );
}
