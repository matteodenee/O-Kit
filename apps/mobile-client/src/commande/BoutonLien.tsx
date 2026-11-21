import { Href, Link } from 'expo-router';
import { Pressable, Text } from 'react-native';

import { ui } from './ui';

type Props = {
  href: Href;
  label: string;
  variante?: 'contour' | 'accent' | 'texte';
};

/**
 * Bouton de navigation, même construction que dans addresses.tsx (Link asChild + Pressable).
 * Le style doit rester un objet : sous Link asChild, un style en fonction ({ pressed }) est perdu sur le web.
 */
export function BoutonLien({ href, label, variante = 'contour' }: Props) {
  const style =
    variante === 'accent' ? ui.accentButton : variante === 'contour' ? ui.backButton : undefined;
  const texte =
    variante === 'accent' ? ui.accentButtonText : variante === 'contour' ? ui.backButtonText : ui.actionText;
  return (
    <Link asChild href={href}>
      <Pressable accessibilityRole="button" style={style}>
        <Text style={texte}>{label}</Text>
      </Pressable>
    </Link>
  );
}
