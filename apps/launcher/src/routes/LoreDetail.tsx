import { Navigate, useNavigate, useParams } from "react-router";
import { ContentDetail } from "../components/content-pages/ContentDetail";
import { findLoreCategory, LORE_DETAIL_BY_ID } from "../lib/content-data";

export function LoreDetail() {
  const { slug = "" } = useParams<{ slug: string }>();
  const navigate = useNavigate();
  const cat = findLoreCategory(slug);

  if (!cat) {
    return <Navigate to="/lore" replace state={{ level: 2 }} />;
  }

  const detail = LORE_DETAIL_BY_ID[cat.id] ?? LORE_DETAIL_BY_ID.monde;

  return (
    <ContentDetail
      variant="lore"
      category={cat}
      intro={detail.intro}
      sections={detail.sections}
      onBack={() => navigate("/lore", { state: { level: 2 } })}
    />
  );
}
