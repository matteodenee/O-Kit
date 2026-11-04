import { Link, Redirect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import {
  archiveUserAddress,
  createUserAddress,
  listUserAddresses,
  setDefaultUserAddress,
  updateUserAddress,
  UserAddress,
  UserAddressInput,
} from '../api/userAddresses';
import { useAuth } from '../auth/AuthProvider';

type AddressForm = {
  [Key in keyof UserAddressInput]: string;
};

const emptyForm: AddressForm = {
  label: '',
  recipientFirstName: '',
  recipientLastName: '',
  phoneNumber: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  region: '',
  postalCode: '',
  countryCode: 'FR',
};

export default function AddressesScreen() {
  const { accessToken, authenticatedFetch, isReady, user } = useAuth();
  const [addresses, setAddresses] = useState<UserAddress[]>([]);
  const [form, setForm] = useState<AddressForm>(emptyForm);
  const [editingAddressId, setEditingAddressId] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [pendingArchiveId, setPendingArchiveId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadAddresses = useCallback(async () => {
    if (!accessToken) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setAddresses(await listUserAddresses(authenticatedFetch));
    } catch (loadError) {
      setError(
        loadError instanceof Error ? loadError.message : 'Les adresses sont indisponibles.'
      );
    } finally {
      setLoading(false);
    }
  }, [accessToken, authenticatedFetch]);

  useEffect(() => {
    if (!isReady || !accessToken) {
      return;
    }

    let active = true;
    listUserAddresses(authenticatedFetch)
      .then((loadedAddresses) => {
        if (active) {
          setAddresses(loadedAddresses);
        }
      })
      .catch((loadError: unknown) => {
        if (active) {
          setError(
            loadError instanceof Error
              ? loadError.message
              : 'Les adresses sont indisponibles.'
          );
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [accessToken, authenticatedFetch, isReady]);

  if (!isReady) {
    return (
      <View style={styles.loadingPage}>
        <ActivityIndicator color="#c95f2b" size="large" />
      </View>
    );
  }

  if (!accessToken) {
    return <Redirect href="/" />;
  }

  function startCreate() {
    setEditingAddressId(null);
    setForm({
      ...emptyForm,
      recipientFirstName: user?.given_name ?? '',
      recipientLastName: user?.family_name ?? '',
    });
    setMessage(null);
    setError(null);
    setShowForm(true);
  }

  function startEdit(address: UserAddress) {
    setEditingAddressId(address.id);
    setForm({
      label: address.label ?? '',
      recipientFirstName: address.recipientFirstName,
      recipientLastName: address.recipientLastName,
      phoneNumber: address.phoneNumber,
      addressLine1: address.addressLine1,
      addressLine2: address.addressLine2 ?? '',
      city: address.city,
      region: address.region ?? '',
      postalCode: address.postalCode ?? '',
      countryCode: address.countryCode,
    });
    setMessage(null);
    setError(null);
    setShowForm(true);
  }

  function cancelForm() {
    setEditingAddressId(null);
    setShowForm(false);
    setForm(emptyForm);
  }

  function updateField<Key extends keyof AddressForm>(field: Key, value: AddressForm[Key]) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  async function saveAddress() {
    const requiredValues = [
      form.recipientFirstName,
      form.recipientLastName,
      form.phoneNumber,
      form.addressLine1,
      form.city,
    ];
    if (requiredValues.some((value) => !value.trim())) {
      setError('Destinataire, téléphone, adresse et ville sont obligatoires.');
      return;
    }
    if (!/^[A-Za-z]{2}$/.test(form.countryCode.trim())) {
      setError('Le pays doit utiliser un code ISO sur deux lettres, par exemple FR.');
      return;
    }

    const input: UserAddressInput = {
      label: form.label.trim() || null,
      recipientFirstName: form.recipientFirstName.trim(),
      recipientLastName: form.recipientLastName.trim(),
      phoneNumber: form.phoneNumber.trim(),
      addressLine1: form.addressLine1.trim(),
      addressLine2: form.addressLine2.trim() || null,
      city: form.city.trim(),
      region: form.region.trim() || null,
      postalCode: form.postalCode.trim() || null,
      countryCode: form.countryCode.trim().toUpperCase(),
    };

    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      if (editingAddressId) {
        await updateUserAddress(authenticatedFetch, editingAddressId, input);
        setMessage('Adresse mise à jour.');
      } else {
        await createUserAddress(authenticatedFetch, input);
        setMessage('Adresse ajoutée.');
      }
      cancelForm();
      await loadAddresses();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : "L'adresse n'a pas pu être enregistrée.");
    } finally {
      setBusy(false);
    }
  }

  async function chooseDefault(addressId: string) {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await setDefaultUserAddress(authenticatedFetch, addressId);
      setMessage('Adresse par défaut mise à jour.');
      await loadAddresses();
    } catch (defaultError) {
      setError(
        defaultError instanceof Error
          ? defaultError.message
          : "L'adresse par défaut n'a pas pu être modifiée."
      );
    } finally {
      setBusy(false);
    }
  }

  function requestArchive(address: UserAddress) {
    if (address.defaultAddress && addresses.length > 1) {
      setError("Choisis d'abord une autre adresse par défaut avant de masquer celle-ci.");
      setMessage(null);
      return;
    }
    setPendingArchiveId(address.id);
    setError(null);
    setMessage(null);
  }

  async function confirmArchive(addressId: string) {
    setBusy(true);
    setError(null);
    try {
      await archiveUserAddress(authenticatedFetch, addressId);
      setPendingArchiveId(null);
      setMessage('Adresse masquée.');
      await loadAddresses();
    } catch (archiveError) {
      setError(
        archiveError instanceof Error ? archiveError.message : "L'adresse n'a pas pu être masquée."
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.page}>
      <View style={styles.container}>
        <View style={styles.header}>
          <View style={styles.headerCopy}>
            <Text style={styles.eyebrow}>COMPTE ACHETEUR</Text>
            <Text style={styles.title}>Mes adresses</Text>
            <Text style={styles.subtitle}>
              Enregistre tes destinations de livraison. La première devient ton adresse par défaut.
            </Text>
          </View>
          <View style={styles.headerActions}>
            <Link asChild href="/?profile=1">
              <Pressable accessibilityRole="button" style={styles.backButton}>
                <Text style={styles.backButtonText}>Retour au profil</Text>
              </Pressable>
            </Link>
            <Link asChild href="/catalogue">
              <Pressable accessibilityRole="button" style={styles.catalogueButton}>
                <Text style={styles.catalogueButtonText}>Catalogue</Text>
              </Pressable>
            </Link>
          </View>
        </View>

        {error ? <Notice message={error} tone="error" /> : null}
        {message ? <Notice message={message} tone="success" /> : null}

        {showForm ? (
          <AddressFormCard
            busy={busy}
            editing={Boolean(editingAddressId)}
            form={form}
            onCancel={cancelForm}
            onChange={updateField}
            onSave={() => void saveAddress()}
          />
        ) : (
          <Pressable
            accessibilityRole="button"
            disabled={busy}
            onPress={startCreate}
            style={({ pressed }) => [
              styles.addButton,
              pressed && styles.pressed,
              busy && styles.disabled,
            ]}
          >
            <Text style={styles.addButtonText}>+ Ajouter une adresse</Text>
          </Pressable>
        )}

        {loading ? (
          <View style={styles.loadingBlock}>
            <ActivityIndicator color="#c95f2b" />
            <Text style={styles.muted}>Chargement de tes adresses…</Text>
          </View>
        ) : null}

        {!loading && addresses.length === 0 ? (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>Aucune adresse enregistrée</Text>
            <Text style={styles.emptyText}>
              Ajoute ta première adresse. Elle sera automatiquement utilisée par défaut.
            </Text>
          </View>
        ) : null}

        <View style={styles.addressGrid}>
          {addresses.map((address) => (
            <AddressCard
              address={address}
              busy={busy}
              key={address.id}
              onArchive={() => requestArchive(address)}
              onCancelArchive={() => setPendingArchiveId(null)}
              onConfirmArchive={() => void confirmArchive(address.id)}
              onEdit={() => startEdit(address)}
              onSetDefault={() => void chooseDefault(address.id)}
              pendingArchive={pendingArchiveId === address.id}
            />
          ))}
        </View>
      </View>
    </ScrollView>
  );
}

type AddressFormCardProps = {
  busy: boolean;
  editing: boolean;
  form: AddressForm;
  onCancel: () => void;
  onChange: <Key extends keyof AddressForm>(field: Key, value: AddressForm[Key]) => void;
  onSave: () => void;
};

function AddressFormCard({ busy, editing, form, onCancel, onChange, onSave }: AddressFormCardProps) {
  return (
    <View style={styles.formCard}>
      <Text style={styles.cardTitle}>{editing ? "Modifier l'adresse" : 'Nouvelle adresse'}</Text>
      <View style={styles.formGrid}>
        <Field label="Libellé" onChangeText={(value) => onChange('label', value)} placeholder="Maison" value={form.label} />
        <Field label="Prénom du destinataire *" onChangeText={(value) => onChange('recipientFirstName', value)} value={form.recipientFirstName} />
        <Field label="Nom du destinataire *" onChangeText={(value) => onChange('recipientLastName', value)} value={form.recipientLastName} />
        <Field keyboardType="phone-pad" label="Téléphone *" onChangeText={(value) => onChange('phoneNumber', value)} value={form.phoneNumber} />
        <Field label="Adresse *" onChangeText={(value) => onChange('addressLine1', value)} placeholder="12 rue des Sports" value={form.addressLine1} />
        <Field label="Complément" onChangeText={(value) => onChange('addressLine2', value)} placeholder="Bâtiment, appartement…" value={form.addressLine2} />
        <Field label="Ville *" onChangeText={(value) => onChange('city', value)} value={form.city} />
        <Field label="Région / État" onChangeText={(value) => onChange('region', value)} value={form.region} />
        <Field label="Code postal" onChangeText={(value) => onChange('postalCode', value)} value={form.postalCode} />
        <Field autoCapitalize="characters" label="Pays (ISO) *" maxLength={2} onChangeText={(value) => onChange('countryCode', value)} placeholder="FR" value={form.countryCode} />
      </View>
      <View style={styles.formActions}>
        <Pressable accessibilityRole="button" disabled={busy} onPress={onCancel} style={styles.secondaryAction}>
          <Text style={styles.secondaryActionText}>Annuler</Text>
        </Pressable>
        <Pressable accessibilityRole="button" disabled={busy} onPress={onSave} style={[styles.primaryAction, busy && styles.disabled]}>
          <Text style={styles.primaryActionText}>{busy ? 'Enregistrement…' : 'Enregistrer'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

type AddressCardProps = {
  address: UserAddress;
  busy: boolean;
  onArchive: () => void;
  onCancelArchive: () => void;
  onConfirmArchive: () => void;
  onEdit: () => void;
  onSetDefault: () => void;
  pendingArchive: boolean;
};

function AddressCard({ address, busy, onArchive, onCancelArchive, onConfirmArchive, onEdit, onSetDefault, pendingArchive }: AddressCardProps) {
  return (
    <View style={[styles.addressCard, address.defaultAddress && styles.defaultCard]}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle}>{address.label || 'Adresse'}</Text>
        {address.defaultAddress ? <Text style={styles.defaultBadge}>PAR DÉFAUT</Text> : null}
      </View>
      <Text style={styles.recipient}>{address.recipientFirstName} {address.recipientLastName}</Text>
      <Text style={styles.addressLine}>{address.addressLine1}</Text>
      {address.addressLine2 ? <Text style={styles.addressLine}>{address.addressLine2}</Text> : null}
      <Text style={styles.addressLine}>{[address.postalCode, address.city].filter(Boolean).join(' ')}</Text>
      {address.region ? <Text style={styles.addressLine}>{address.region}</Text> : null}
      <Text style={styles.addressLine}>{address.countryCode}</Text>
      <Text style={styles.phone}>{address.phoneNumber}</Text>

      {pendingArchive ? (
        <View style={styles.confirmBox}>
          <Text style={styles.confirmText}>Masquer définitivement cette adresse ?</Text>
          <View style={styles.inlineActions}>
            <Pressable disabled={busy} onPress={onCancelArchive} style={styles.secondarySmall}>
              <Text style={styles.secondarySmallText}>Annuler</Text>
            </Pressable>
            <Pressable disabled={busy} onPress={onConfirmArchive} style={styles.dangerSmall}>
              <Text style={styles.dangerSmallText}>Confirmer</Text>
            </Pressable>
          </View>
        </View>
      ) : (
        <View style={styles.cardActions}>
          <Pressable disabled={busy} onPress={onEdit}><Text style={styles.actionText}>Modifier</Text></Pressable>
          {!address.defaultAddress ? (
            <Pressable disabled={busy} onPress={onSetDefault}><Text style={styles.actionText}>Définir par défaut</Text></Pressable>
          ) : null}
          <Pressable disabled={busy} onPress={onArchive}><Text style={styles.dangerText}>Masquer</Text></Pressable>
        </View>
      )}
    </View>
  );
}

type FieldProps = {
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
  keyboardType?: 'default' | 'phone-pad';
  label: string;
  maxLength?: number;
  onChangeText: (value: string) => void;
  placeholder?: string;
  value: string;
};

function Field({ autoCapitalize = 'words', keyboardType = 'default', label, maxLength, onChangeText, placeholder, value }: FieldProps) {
  return (
    <View style={styles.field}>
      <Text style={styles.fieldLabel}>{label}</Text>
      <TextInput
        autoCapitalize={autoCapitalize}
        keyboardType={keyboardType}
        maxLength={maxLength}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor="#91897d"
        style={styles.input}
        value={value}
      />
    </View>
  );
}

function Notice({ message, tone }: { message: string; tone: 'error' | 'success' }) {
  return (
    <View style={[styles.notice, tone === 'error' ? styles.noticeError : styles.noticeSuccess]}>
      <Text style={tone === 'error' ? styles.noticeErrorText : styles.noticeSuccessText}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  loadingPage: { alignItems: 'center', backgroundColor: '#f3eee4', flex: 1, justifyContent: 'center' },
  page: { backgroundColor: '#f3eee4', flexGrow: 1, padding: 20 },
  container: { alignSelf: 'center', maxWidth: 1080, width: '100%' },
  header: { alignItems: 'flex-start', flexDirection: 'row', flexWrap: 'wrap', gap: 20, justifyContent: 'space-between', marginBottom: 26 },
  headerCopy: { maxWidth: 680 },
  eyebrow: { color: '#b75328', fontSize: 11, fontWeight: '900', letterSpacing: 1.8, marginBottom: 8 },
  title: { color: '#17322c', fontSize: 40, fontWeight: '800', letterSpacing: -1.4 },
  subtitle: { color: '#615f59', fontSize: 15, lineHeight: 23, marginTop: 10 },
  headerActions: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  backButton: { borderColor: '#b7aa98', borderRadius: 12, borderWidth: 1, paddingHorizontal: 16, paddingVertical: 12 },
  backButtonText: { color: '#17322c', fontSize: 13, fontWeight: '800' },
  catalogueButton: { backgroundColor: '#c95f2b', borderRadius: 12, paddingHorizontal: 16, paddingVertical: 12 },
  catalogueButtonText: { color: '#fffaf1', fontSize: 13, fontWeight: '800' },
  addButton: { alignItems: 'center', alignSelf: 'flex-start', backgroundColor: '#c95f2b', borderRadius: 13, marginBottom: 22, minHeight: 50, paddingHorizontal: 20, justifyContent: 'center' },
  addButtonText: { color: '#fffaf1', fontSize: 15, fontWeight: '800' },
  pressed: { opacity: 0.8, transform: [{ scale: 0.99 }] },
  disabled: { opacity: 0.5 },
  loadingBlock: { alignItems: 'center', flexDirection: 'row', gap: 10, paddingVertical: 24 },
  muted: { color: '#79736a', fontSize: 14 },
  emptyCard: { backgroundColor: '#fffaf1', borderColor: '#ded3c2', borderRadius: 18, borderStyle: 'dashed', borderWidth: 1, padding: 28 },
  emptyTitle: { color: '#17322c', fontSize: 20, fontWeight: '800' },
  emptyText: { color: '#6e6960', fontSize: 14, lineHeight: 21, marginTop: 7 },
  addressGrid: { gap: 16 },
  addressCard: { backgroundColor: '#fffaf1', borderColor: '#ded3c2', borderRadius: 18, borderWidth: 1, padding: 22 },
  defaultCard: { borderColor: '#24705e', borderWidth: 2 },
  cardHeader: { alignItems: 'center', flexDirection: 'row', flexWrap: 'wrap', gap: 10, justifyContent: 'space-between', marginBottom: 12 },
  cardTitle: { color: '#17322c', fontSize: 20, fontWeight: '800' },
  defaultBadge: { backgroundColor: '#dcebe5', borderRadius: 999, color: '#1b614f', fontSize: 10, fontWeight: '900', letterSpacing: 1, paddingHorizontal: 10, paddingVertical: 6 },
  recipient: { color: '#2f4841', fontSize: 16, fontWeight: '800', marginBottom: 5 },
  addressLine: { color: '#615f59', fontSize: 14, lineHeight: 20 },
  phone: { color: '#615f59', fontSize: 14, fontWeight: '700', marginTop: 8 },
  cardActions: { borderTopColor: '#e3d9ca', borderTopWidth: 1, flexDirection: 'row', flexWrap: 'wrap', gap: 18, marginTop: 18, paddingTop: 15 },
  actionText: { color: '#1f6655', fontSize: 13, fontWeight: '800' },
  dangerText: { color: '#a23d2b', fontSize: 13, fontWeight: '800' },
  formCard: { backgroundColor: '#fffaf1', borderColor: '#d2c5b3', borderRadius: 18, borderWidth: 1, marginBottom: 24, padding: 24 },
  formGrid: { gap: 14, marginTop: 18 },
  field: { gap: 7 },
  fieldLabel: { color: '#344b45', fontSize: 12, fontWeight: '800' },
  input: { backgroundColor: '#f5efe4', borderColor: '#ded3c2', borderRadius: 12, borderWidth: 1, color: '#17322c', fontSize: 15, minHeight: 49, paddingHorizontal: 15 },
  formActions: { flexDirection: 'row', gap: 12, justifyContent: 'flex-end', marginTop: 22 },
  primaryAction: { alignItems: 'center', backgroundColor: '#c95f2b', borderRadius: 12, justifyContent: 'center', minHeight: 48, paddingHorizontal: 20 },
  primaryActionText: { color: '#fffaf1', fontSize: 14, fontWeight: '800' },
  secondaryAction: { alignItems: 'center', borderColor: '#c9bfae', borderRadius: 12, borderWidth: 1, justifyContent: 'center', minHeight: 48, paddingHorizontal: 20 },
  secondaryActionText: { color: '#17322c', fontSize: 14, fontWeight: '800' },
  confirmBox: { backgroundColor: '#fff0eb', borderRadius: 12, marginTop: 18, padding: 14 },
  confirmText: { color: '#723327', fontSize: 13, fontWeight: '700' },
  inlineActions: { flexDirection: 'row', gap: 10, marginTop: 12 },
  secondarySmall: { borderColor: '#bdaea0', borderRadius: 9, borderWidth: 1, paddingHorizontal: 13, paddingVertical: 9 },
  secondarySmallText: { color: '#4f4a43', fontSize: 12, fontWeight: '800' },
  dangerSmall: { backgroundColor: '#a23d2b', borderRadius: 9, paddingHorizontal: 13, paddingVertical: 9 },
  dangerSmallText: { color: '#fff', fontSize: 12, fontWeight: '800' },
  notice: { borderRadius: 12, marginBottom: 18, padding: 14 },
  noticeError: { backgroundColor: '#fde9e5' },
  noticeSuccess: { backgroundColor: '#e1f1e9' },
  noticeErrorText: { color: '#8b3527', fontSize: 13, lineHeight: 19 },
  noticeSuccessText: { color: '#1f6655', fontSize: 13, lineHeight: 19 },
});
